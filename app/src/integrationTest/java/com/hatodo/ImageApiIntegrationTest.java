package com.hatodo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Profile pictures, against real Postgres and real MinIO.
 *
 * <p>The container is the point: bucket creation at startup, and bytes making
 * the round trip through the object store, are exactly the configuration that
 * an in-memory stand-in would leave unexercised.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class ImageApiIntegrationTest extends ApiIntegrationTest {

    private record Member(String username, UUID id, Session session) {}

    private Member givenMember(String label) {
        String username = label + "-" + UUID.randomUUID().toString().substring(0, 8);
        Session admin = signIn(ADMIN_USERNAME, ADMIN_PASSWORD);
        ResponseEntity<Map> created = rest.exchange("/api/admin/users", HttpMethod.POST,
                new HttpEntity<>("{\"username\":\"%s\",\"displayName\":\"%s\",\"password\":\"pw\"}"
                        .formatted(username, username), headersFor(admin)), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return new Member(username, UUID.fromString((String) created.getBody().get("id")),
                signIn(username, "pw"));
    }

    private static byte[] png() throws IOException {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }

    private ResponseEntity<Map> upload(Session session, byte[] content, String filename) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        HttpHeaders headers = headersFor(session);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange("/api/me/avatar", HttpMethod.PUT, new HttpEntity<>(body, headers),
                Map.class);
    }

    @Test
    void storesAPictureAndServesItBack() throws IOException {
        Member ana = givenMember("avatar-owner");

        ResponseEntity<Map> uploaded = upload(ana.session(), png(), "me.png");
        assertThat(uploaded.getStatusCode()).isEqualTo(HttpStatus.OK);
        String imageId = (String) uploaded.getBody().get("avatarImageId");
        assertThat(imageId).isNotNull();

        ResponseEntity<byte[]> served = rest.exchange("/api/images/" + imageId, HttpMethod.GET,
                new HttpEntity<>(headersFor(ana.session())), byte[].class);

        assertThat(served.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(served.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(served.getBody()).isEqualTo(png());
    }

    @Test
    void anAvatarIsReadableWithoutASessionSoTheSignInScreenCanDrawIt() throws IOException {
        // adr/0019: the one field ever added to the public roster is the face,
        // and a face nobody can fetch is not a face.
        Member ana = givenMember("anonymous-readable");
        String imageId = (String) upload(ana.session(), png(), "me.png").getBody().get("avatarImageId");

        ResponseEntity<byte[]> served = rest.getForEntity("/api/images/" + imageId, byte[].class);

        assertThat(served.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void theSignInRosterCarriesTheFaceAndStillNothingElse() throws IOException {
        Member ana = givenMember("roster-face");
        upload(ana.session(), png(), "me.png");

        ResponseEntity<List> roster = rest.getForEntity("/api/auth/users", List.class);

        Map<String, Object> entry = ((List<Map<String, Object>>) roster.getBody()).stream()
                .filter(user -> ana.username().equals(user.get("username")))
                .findFirst()
                .orElseThrow();
        assertThat(entry.keySet())
                .as("adr/0009 still forbids role and passwordRequired here")
                .containsExactlyInAnyOrder("id", "username", "displayName", "avatarImageId");
        assertThat(entry.get("avatarImageId")).isNotNull();
    }

    @Test
    void refusesAnythingThatIsNotOneOfTheThreeImageTypes() {
        Member ana = givenMember("svg-uploader");
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        // Named .png on purpose: the filename is the uploader's to choose, so
        // the answer has to come from the bytes.
        ResponseEntity<Map> response = upload(ana.session(), svg, "innocent.png");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void replacingAPictureLeavesTheOldOneUnreadable() throws IOException {
        Member ana = givenMember("replacer");
        String first = (String) upload(ana.session(), png(), "a.png").getBody().get("avatarImageId");
        String second = (String) upload(ana.session(), png(), "b.png").getBody().get("avatarImageId");

        assertThat(second).isNotEqualTo(first);
        assertThat(rest.getForEntity("/api/images/" + first, byte[].class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest.getForEntity("/api/images/" + second, byte[].class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void removingAPictureLeavesTheMemberWithNone() throws IOException {
        Member ana = givenMember("remover");
        String imageId = (String) upload(ana.session(), png(), "me.png").getBody().get("avatarImageId");

        ResponseEntity<Void> removed = rest.exchange("/api/me/avatar", HttpMethod.DELETE,
                new HttpEntity<>(headersFor(ana.session())), Void.class);

        assertThat(removed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(rest.getForEntity("/api/images/" + imageId, byte[].class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        ResponseEntity<Map> me = rest.exchange("/api/me", HttpMethod.GET,
                new HttpEntity<>(headersFor(ana.session())), Map.class);
        assertThat(me.getBody().get("avatarImageId")).isNull();
    }

    @Test
    void deletingAMemberTakesTheirPictureWithThem() throws IOException {
        // The foreign keys alone would not: deleting a user only nulls
        // stored_image.uploaded_by, so without this the bytes outlive the
        // account for good. Caught by counting objects in dev's bucket after
        // three runs of this suite.
        Member ana = givenMember("departing");
        String imageId = (String) upload(ana.session(), png(), "me.png").getBody()
                .get("avatarImageId");
        assertThat(rest.getForEntity("/api/images/" + imageId, byte[].class).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        Session admin = signIn(ADMIN_USERNAME, ADMIN_PASSWORD);
        ResponseEntity<Void> removed = rest.exchange("/api/admin/users/" + ana.id(),
                HttpMethod.DELETE, new HttpEntity<>(headersFor(admin)), Void.class);
        assertThat(removed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(rest.getForEntity("/api/images/" + imageId, byte[].class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void anonymousCallersStillCannotUploadOne() throws IOException {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(png()));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<Map> response = rest.exchange("/api/me/avatar", HttpMethod.PUT,
                new HttpEntity<>(body, headers), Map.class);

        assertThat(response.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    @Test
    void anUnknownImageIsNotThere() {
        assertThat(rest.getForEntity("/api/images/" + UUID.randomUUID(), byte[].class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
