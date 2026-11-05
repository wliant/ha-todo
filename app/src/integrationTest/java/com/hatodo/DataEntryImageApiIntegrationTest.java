package com.hatodo;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * An entry's pictures are as private as its collection (requirements/0006 R61,
 * adr/0032).
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class DataEntryImageApiIntegrationTest extends ApiIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private record Member(String username, UUID id, Session session) {}

    private Member givenMember(String label) {
        String username = label + "-" + UUID.randomUUID().toString().substring(0, 8);
        Session admin = signIn(ADMIN_USERNAME, ADMIN_PASSWORD);
        ResponseEntity<Map> created = rest.exchange("/api/admin/users", HttpMethod.POST,
                new HttpEntity<>("{\"username\":\"%s\",\"displayName\":\"%s\",\"password\":\"pw-%s\"}"
                        .formatted(username, username, username), headersFor(admin)), Map.class);
        UUID id = UUID.fromString((String) created.getBody().get("id"));
        return new Member(username, id, signIn(username, "pw-" + username));
    }

    private static byte[] png() throws IOException {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }

    private String upload(Session session) throws IOException {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(png()) {
            @Override
            public String getFilename() {
                return "step.png";
            }
        });
        HttpHeaders headers = headersFor(session);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return (String) rest.exchange("/api/images", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class).getBody().get("id");
    }

    private String newCollection(Member member, String name) {
        return (String) rest.exchange("/api/data-lists", HttpMethod.POST, new HttpEntity<>(
                "{\"name\":\"%s\",\"icon\":\"GROCERIES\",\"memberIds\":[]}".formatted(name),
                headersFor(member.session())), Map.class).getBody().get("id");
    }

    private void share(Member owner, String collectionId, long version, Member with) {
        rest.exchange("/api/data-lists/" + collectionId + "/access", HttpMethod.PUT,
                new HttpEntity<>("{\"version\":%d,\"memberIds\":[\"%s\"]}"
                        .formatted(version, with.id()), headersFor(owner.session())), List.class);
    }

    private String addEntryWith(Member member, String collectionId, String imageId) {
        return (String) rest.exchange("/api/data-lists/" + collectionId + "/entries",
                HttpMethod.POST, new HttpEntity<>(
                        "{\"name\":\"With a photo\",\"detailHtml\":\"<img src=\\\"/api/images/%s\\\">\"}"
                                .formatted(imageId), headersFor(member.session())), Map.class)
                .getBody().get("id");
    }

    private HttpStatus statusReading(String imageId, Session session) {
        return (HttpStatus) rest.exchange("/api/images/" + imageId, HttpMethod.GET,
                new HttpEntity<>(headersFor(session)), byte[].class).getStatusCode();
    }

    @Test
    void anEntryPictureIsReadableByEveryGrantHolder() throws IOException {
        Member ana = givenMember("img-owner");
        Member ben = givenMember("img-peer");
        String id = newCollection(ana, "Shared " + ana.username());
        share(ana, id, 0, ben);
        String imageId = upload(ana.session());
        addEntryWith(ana, id, imageId);

        assertThat(statusReading(imageId, ben.session())).isEqualTo(HttpStatus.OK);
    }

    @Test
    void anEntryPictureIsNotReadableWithoutAGrant() throws IOException {
        Member ana = givenMember("img-private");
        Member ben = givenMember("img-outsider");
        String id = newCollection(ana, "Private " + ana.username());
        String imageId = upload(ana.session());
        addEntryWith(ana, id, imageId);

        // 404, not 403: which collection it belongs to is itself hidden.
        assertThat(statusReading(imageId, ben.session())).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void anEntryPictureIsNotReadableAnonymously() throws IOException {
        Member ana = givenMember("img-anon");
        String id = newCollection(ana, "Recipes " + ana.username());
        String imageId = upload(ana.session());
        addEntryWith(ana, id, imageId);

        // The endpoint is permitAll so the sign-in screen can draw avatars,
        // which is exactly why a detail picture has to answer for itself.
        assertThat(rest.getForEntity("/api/images/" + imageId, byte[].class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * Previously broken, and the reason the editor's own preview did not work
     * between pasting a picture and saving: {@code mayRead} answered no for
     * every unclaimed picture, including to whoever had just uploaded it.
     * ImageUploadController has documented this behaviour since it was written.
     */
    @Test
    void anUnclaimedPictureIsReadableByWhoeverUploadedIt() throws IOException {
        Member ana = givenMember("img-unclaimed-mine");

        String imageId = upload(ana.session());

        assertThat(statusReading(imageId, ana.session())).isEqualTo(HttpStatus.OK);
    }

    @Test
    void anUnclaimedPictureIsReadableByNobodyElse() throws IOException {
        Member ana = givenMember("img-unclaimed");
        Member ben = givenMember("img-unclaimed-other");

        String imageId = upload(ana.session());

        assertThat(statusReading(imageId, ben.session())).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aMemberCannotClaimSomebodyElsesPicture() throws IOException {
        // An id alone must not be enough: otherwise guessing one pulls a
        // picture onto a collection its uploader never chose.
        Member ana = givenMember("img-victim");
        Member thief = givenMember("img-thief");
        String imageId = upload(ana.session());
        String theirs = newCollection(thief, "Theirs " + thief.username());

        addEntryWith(thief, theirs, imageId);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM stored_image WHERE id = ?::uuid AND entry_id IS NOT NULL",
                Long.class, imageId)).isZero();
        assertThat(statusReading(imageId, thief.session())).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aPictureAnotherOwnerHoldsCannotBeClaimedByAnEntry() throws IOException {
        // The service's half of the exclusivity rule. The picture is given an
        // item owner directly rather than through the item API, because a list
        // only accepts a detailHtml once it declares a DETAIL field - which is
        // field-configuration mechanics, not what this is about.
        Member ana = givenMember("img-crossclaim");
        String imageId = upload(ana.session());
        UUID someItem = UUID.randomUUID();
        jdbc.update("INSERT INTO todo_list (id, owner_id, name, icon) "
                + "VALUES (?::uuid, ?::uuid, ?, 'HOME')",
                someItem, ana.id().toString(), "Holder " + ana.username());
        jdbc.update("INSERT INTO todo_item (id, list_id, description, position) "
                + "VALUES (?::uuid, ?::uuid, 'Fix it', 0)", someItem, someItem);
        jdbc.update("UPDATE stored_image SET item_id = ?::uuid WHERE id = ?::uuid",
                someItem, imageId);

        String collectionId = newCollection(ana, "Recipes " + ana.username());
        addEntryWith(ana, collectionId, imageId);

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT item_id, entry_id FROM stored_image WHERE id = ?::uuid", imageId);
        assertThat(row.get("item_id")).isNotNull();
        assertThat(row.get("entry_id")).isNull();
    }

    @Test
    void theDatabaseRefusesTwoOwnersOnOnePicture() throws IOException {
        // The database's half of the same rule: num_nonnulls(...) <= 1, which
        // is what makes the service check a guard rather than the only defence.
        Member ana = givenMember("img-twoowners");
        String id = newCollection(ana, "Recipes " + ana.username());
        String imageId = upload(ana.session());
        String entryId = addEntryWith(ana, id, imageId);
        assertThat(jdbc.queryForObject(
                "SELECT entry_id FROM stored_image WHERE id = ?::uuid", String.class, imageId))
                .isEqualTo(entryId);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
                "UPDATE stored_image SET item_id = ?::uuid WHERE id = ?::uuid",
                UUID.randomUUID().toString(), imageId))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test
    void deletingAnEntryForgetsItsPictures() throws IOException {
        Member ana = givenMember("img-delete");
        String id = newCollection(ana, "Recipes " + ana.username());
        String imageId = upload(ana.session());
        String entryId = addEntryWith(ana, id, imageId);

        rest.exchange("/api/data-entries/" + entryId + "?version=0", HttpMethod.DELETE,
                new HttpEntity<>(headersFor(ana.session())), Void.class);

        // The cascade takes the row; forgetPicturesOf takes the bytes.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM stored_image WHERE id = ?::uuid",
                Long.class, imageId)).isZero();
    }

    @Test
    void deletingACollectionForgetsEveryEntrysPictures() throws IOException {
        Member ana = givenMember("img-delete-coll");
        String id = newCollection(ana, "Recipes " + ana.username());
        String imageId = upload(ana.session());
        addEntryWith(ana, id, imageId);

        rest.exchange("/api/data-lists/" + id, HttpMethod.DELETE,
                new HttpEntity<>(headersFor(ana.session())), Void.class);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM stored_image WHERE id = ?::uuid",
                Long.class, imageId)).isZero();
    }

    @Test
    void theUnclaimedIndexNoLongerCountsOwnedPicturesAsOrphans() throws IOException {
        // V907's index predated rule_id and entry_id, so it reported every
        // rule-owned and entry-owned picture as an unclaimed orphan. Nothing
        // sweeps orphans today, but a sweeper written against it would have
        // deleted them (adr/0032).
        Member ana = givenMember("img-orphanindex");
        String id = newCollection(ana, "Recipes " + ana.username());
        String imageId = upload(ana.session());
        addEntryWith(ana, id, imageId);

        Long orphans = jdbc.queryForObject("""
                SELECT COUNT(*) FROM stored_image
                WHERE id = ?::uuid
                  AND item_id IS NULL AND rule_id IS NULL AND entry_id IS NULL
                  AND purpose = 'DETAIL'
                """, Long.class, imageId);
        assertThat(orphans).isZero();
    }
}
