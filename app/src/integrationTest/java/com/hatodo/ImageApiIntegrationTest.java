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

    @org.springframework.beans.factory.annotation.Autowired
    private com.hatodo.images.StoredImageRepository storedImages;

    @org.springframework.beans.factory.annotation.Autowired
    private com.hatodo.images.ImageStorage storage;

    /**
     * Whether the bytes are still in the store.
     *
     * <p>The API cannot answer this: deleting an item cascade-deletes the
     * image's row, so {@code GET /api/images/{id}} answers 404 whether or not
     * the object behind it was ever removed. Captured before the delete,
     * because afterwards nothing knows the key.
     */
    private boolean objectExists(String objectKey) {
        try (java.io.InputStream stream = storage.open(objectKey)) {
            return stream.read() != -1 || true;
        } catch (Exception gone) {
            return false;
        }
    }

    private String objectKeyOf(String imageId) {
        return storedImages.findById(UUID.fromString(imageId))
                .orElseThrow(() -> new AssertionError("no image " + imageId))
                .getObjectKey();
    }

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

    private ResponseEntity<Map> uploadDetail(Session session, byte[] content) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return "step.png";
            }
        });
        HttpHeaders headers = headersFor(session);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange("/api/images", HttpMethod.POST, new HttpEntity<>(body, headers),
                Map.class);
    }

    private String newList(Member owner, String... memberIds) {
        String members = memberIds.length == 0 ? "[]" : "[\"" + String.join("\",\"", memberIds) + "\"]";
        ResponseEntity<Map> created = rest.exchange("/api/lists", HttpMethod.POST,
                new HttpEntity<>("{\"name\":\"Detail %s\",\"icon\":\"CHECKLIST\",\"memberIds\":%s}"
                        .formatted(UUID.randomUUID(), members), headersFor(owner.session())),
                Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        rest.exchange("/api/lists/" + created.getBody().get("id") + "/fields", HttpMethod.PUT,
                new HttpEntity<>("{\"version\":%d,\"fields\":[{\"kind\":\"DETAIL\"}]}"
                        .formatted(((Number) created.getBody().get("version")).longValue()),
                        headersFor(owner.session())), String.class);
        return (String) created.getBody().get("id");
    }

    @Test
    void aDetailPictureIsAsPrivateAsTheListItIsOn() throws IOException {
        // Without the item link a detail picture would have no owner, and
        // "who may read it" no answer but "anybody signed in" (R19).
        Member ana = givenMember("detail-owner");
        Member stranger = givenMember("detail-stranger");
        String listId = newList(ana);

        String imageId = (String) uploadDetail(ana.session(), png()).getBody().get("id");
        ResponseEntity<Map> item = rest.exchange("/api/lists/" + listId + "/items",
                HttpMethod.POST, new HttpEntity<>(
                        "{\"description\":\"With a picture\",\"detailHtml\":\"<p>step</p><img src=\\\"/api/images/%s\\\">\"}"
                                .formatted(imageId), headersFor(ana.session())), Map.class);
        assertThat(item.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat((String) item.getBody().get("detailHtml")).contains(imageId);

        assertThat(rest.exchange("/api/images/" + imageId, HttpMethod.GET,
                new HttpEntity<>(headersFor(ana.session())), byte[].class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(rest.exchange("/api/images/" + imageId, HttpMethod.GET,
                new HttpEntity<>(headersFor(stranger.session())), byte[].class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest.getForEntity("/api/images/" + imageId, byte[].class).getStatusCode())
                .as("and certainly not anonymously, unlike an avatar")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void sharingTheListSharesItsPicturesToo() throws IOException {
        Member ana = givenMember("detail-sharer");
        Member ben = givenMember("detail-grantee");
        String listId = newList(ana, ben.id().toString());

        String imageId = (String) uploadDetail(ana.session(), png()).getBody().get("id");
        rest.exchange("/api/lists/" + listId + "/items", HttpMethod.POST, new HttpEntity<>(
                "{\"description\":\"Shared picture\",\"detailHtml\":\"<img src=\\\"/api/images/%s\\\">\"}"
                        .formatted(imageId), headersFor(ana.session())), Map.class);

        assertThat(rest.exchange("/api/images/" + imageId, HttpMethod.GET,
                new HttpEntity<>(headersFor(ben.session())), byte[].class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void anUnclaimedPictureIsReadableOnlyByWhoeverUploadedIt() throws IOException {
        // Uploaded into an item that was never saved: nobody else has any
        // claim on it, and it is the orphan adr/0013 tolerates.
        Member ana = givenMember("detail-unclaimed");
        Member other = givenMember("detail-other");

        String imageId = (String) uploadDetail(ana.session(), png()).getBody().get("id");

        assertThat(rest.exchange("/api/images/" + imageId, HttpMethod.GET,
                new HttpEntity<>(headersFor(other.session())), byte[].class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void cannotDragSomebodyElsesPictureOntoYourOwnList() throws IOException {
        // An id alone must not be enough: otherwise guessing one would pull a
        // picture out of a list you cannot see and onto one you can.
        Member ana = givenMember("detail-victim");
        Member thief = givenMember("detail-thief");
        String imageId = (String) uploadDetail(ana.session(), png()).getBody().get("id");
        String theirList = newList(thief);

        rest.exchange("/api/lists/" + theirList + "/items", HttpMethod.POST, new HttpEntity<>(
                "{\"description\":\"Not mine\",\"detailHtml\":\"<img src=\\\"/api/images/%s\\\">\"}"
                        .formatted(imageId), headersFor(thief.session())), Map.class);

        assertThat(rest.exchange("/api/images/" + imageId, HttpMethod.GET,
                new HttpEntity<>(headersFor(thief.session())), byte[].class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void stripsAnAttackOutOfTheDetailBeforeItIsEverStored() throws IOException {
        Member ana = givenMember("detail-xss");
        String listId = newList(ana);

        ResponseEntity<Map> item = rest.exchange("/api/lists/" + listId + "/items",
                HttpMethod.POST, new HttpEntity<>(
                        "{\"description\":\"Nasty\",\"detailHtml\":"
                        + "\"<p>ok</p><script>alert(1)</script><img src=x onerror=alert(1)>\"}",
                        headersFor(ana.session())), Map.class);

        String stored = (String) item.getBody().get("detailHtml");
        assertThat(stored).contains("ok").doesNotContain("script").doesNotContain("onerror");
    }

    @Test
    void deletingAnItemTakesItsPicturesWithIt() throws IOException {
        // The cascade removes the row and leaves the bytes - after which
        // nothing knows the bytes are there at all, so not even a sweeper
        // could find them later.
        Member ana = givenMember("item-deleter");
        String listId = newList(ana);
        String imageId = (String) uploadDetail(ana.session(), png()).getBody().get("id");
        Map item = rest.exchange("/api/lists/" + listId + "/items", HttpMethod.POST,
                new HttpEntity<>(
                        "{\"description\":\"Doomed\",\"detailHtml\":\"<img src=\\\"/api/images/%s\\\">\"}"
                                .formatted(imageId), headersFor(ana.session())), Map.class).getBody();
        String objectKey = objectKeyOf(imageId);
        assertThat(objectExists(objectKey)).isTrue();

        rest.exchange("/api/items/%s?version=%d".formatted(item.get("id"), item.get("version")),
                HttpMethod.DELETE, new HttpEntity<>(headersFor(ana.session())), Void.class);

        assertThat(objectExists(objectKey))
                .as("the bytes, not just the row the cascade would take anyway")
                .isFalse();
    }

    @Test
    void deletingAListTakesEveryPictureOnItToo() throws IOException {
        Member ana = givenMember("list-deleter");
        String listId = newList(ana);
        String imageId = (String) uploadDetail(ana.session(), png()).getBody().get("id");
        rest.exchange("/api/lists/" + listId + "/items", HttpMethod.POST, new HttpEntity<>(
                "{\"description\":\"Doomed too\",\"detailHtml\":\"<img src=\\\"/api/images/%s\\\">\"}"
                        .formatted(imageId), headersFor(ana.session())), Map.class);

        String objectKey = objectKeyOf(imageId);

        rest.exchange("/api/lists/" + listId, HttpMethod.DELETE,
                new HttpEntity<>(headersFor(ana.session())), Void.class);

        assertThat(objectExists(objectKey)).isFalse();
    }

    @Test
    void anUnknownImageIsNotThere() {
        assertThat(rest.getForEntity("/api/images/" + UUID.randomUUID(), byte[].class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
