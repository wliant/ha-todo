package com.hatodo.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hatodo.images.ImagePurpose;
import com.hatodo.images.ImageService;
import com.hatodo.images.StoredImage;
import com.hatodo.images.StoredImageRepository;
import com.hatodo.list.ListIcon;
import com.hatodo.richtext.DetailImages;
import com.hatodo.richtext.HtmlSanitizer;
import com.hatodo.richtext.HtmlText;
import com.hatodo.user.AppUser;
import com.hatodo.user.AppUserPrincipal;
import com.hatodo.user.AppUserRepository;
import com.hatodo.user.CurrentUserProvider;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataEntryServiceTest {

    @Mock private DataEntryRepository entries;
    @Mock private DataListService lists;
    @Mock private DataListRepository dataLists;
    @Mock private AppUserRepository users;
    @Mock private StoredImageRepository storedImages;
    @Mock private ImageService images;
    @Mock private DataEntrySearchQuery searchQuery;

    private final UUID meId = UUID.randomUUID();
    private AppUser me;
    private DataList list;
    private DataEntryService service;

    private static AppUser user(String username, UUID id) {
        AppUser user = AppUser.member(username, username, "$2y$10$hash");
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "enabled", true);
        return user;
    }

    private DataEntry entry(String name) {
        DataEntry entry = new DataEntry(list, name, me);
        ReflectionTestUtils.setField(entry, "id", UUID.randomUUID());
        return entry;
    }

    @BeforeEach
    void setUp() {
        me = user("ana", meId);
        list = new DataList(me, "Recipes", null, ListIcon.GROCERIES);
        ReflectionTestUtils.setField(list, "id", UUID.randomUUID());

        when(users.findById(meId)).thenReturn(Optional.of(me));
        // Assigns an id, as a real save does: GenerationType.UUID is an
        // in-memory generator, so the entity has its id before the insert.
        // A double that skips that makes claimImages look broken when it is
        // not.
        when(entries.save(any())).thenAnswer(call -> {
            DataEntry saved = call.getArgument(0);
            if (saved.getId() == null) {
                ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            }
            return saved;
        });
        when(lists.requireReadable(list.getId())).thenReturn(list);
        when(lists.isReadable(list.getId())).thenReturn(true);

        CurrentUserProvider currentUser = () -> AppUserPrincipal.of(me);
        service = new DataEntryService(entries, lists, dataLists, users, currentUser,
                new HtmlSanitizer(), new HtmlText(), new DetailImages(), new TagNormaliser(),
                storedImages, images, searchQuery, new DataSearchQueryText());
    }

    @Test
    void sanitizesDetailOnWrite() {
        DataEntryResponse created = service.create(list.getId(), new SaveDataEntryRequest(
                null, "Tiramisu", null, "<p>ok</p><script>alert(1)</script>", null));

        assertThat(created.detailHtml()).contains("ok").doesNotContain("script");
    }

    @Test
    void writesDetailTextFromWhatSurvivedSanitizingRatherThanWhatArrived() {
        service.create(list.getId(), new SaveDataEntryRequest(
                null, "Tiramisu", null, "<ul><li>milk</li><li>sugar</li></ul>", null));

        org.mockito.ArgumentCaptor<DataEntry> saved =
                org.mockito.ArgumentCaptor.forClass(DataEntry.class);
        verify(entries).save(saved.capture());
        // A word boundary where the elements were: "milksugar" would be one
        // lexeme matching nothing (adr/0033).
        assertThat(saved.getValue().getDetailText()).isEqualTo("milk sugar");
    }

    @Test
    void anAbsentDetailLeavesBothColumnsNull() {
        service.create(list.getId(),
                new SaveDataEntryRequest(null, "Plain", null, null, null));

        org.mockito.ArgumentCaptor<DataEntry> saved =
                org.mockito.ArgumentCaptor.forClass(DataEntry.class);
        verify(entries).save(saved.capture());
        assertThat(saved.getValue().getDetailHtml()).isNull();
        assertThat(saved.getValue().getDetailText()).isNull();
    }

    @Test
    void normalisesTagsBeforeStoring() {
        service.create(list.getId(), new SaveDataEntryRequest(
                null, "Tiramisu", null, null, List.of("ITALIAN", " desserts ", "italian")));

        org.mockito.ArgumentCaptor<DataEntry> saved =
                org.mockito.ArgumentCaptor.forClass(DataEntry.class);
        verify(entries).save(saved.capture());
        assertThat(saved.getValue().getTags()).containsExactly("desserts", "italian");
        assertThat(saved.getValue().getTagsText()).isEqualTo("desserts italian");
    }

    @Test
    void refusesAnEntryOnACollectionTheCallerCannotSee() {
        UUID invisible = UUID.randomUUID();
        when(lists.requireReadable(invisible))
                .thenThrow(new DataListNotFoundException(invisible));

        assertThatThrownBy(() -> service.create(invisible,
                new SaveDataEntryRequest(null, "Sneaky", null, null, null)))
                .isInstanceOf(DataListNotFoundException.class);
    }

    @Test
    void anEntryOnAnInvisibleCollectionIsNotFoundRatherThanForbidden() {
        DataEntry theirs = entry("Theirs");
        when(entries.findWithList(theirs.getId())).thenReturn(Optional.of(theirs));
        when(lists.isReadable(list.getId())).thenReturn(false);

        assertThatThrownBy(() -> service.get(theirs.getId()))
                .isInstanceOf(DataEntryNotFoundException.class);
    }

    @Test
    void refusesAStaleEditBeforeMutatingAnything() {
        DataEntry existing = entry("Tiramisu");
        when(entries.findWithList(existing.getId())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(existing.getId(),
                new SaveDataEntryRequest(9L, "Renamed", null, null, null)))
                .isInstanceOf(StaleDataEntryException.class);
        assertThat(existing.getName()).isEqualTo("Tiramisu");
    }

    @Test
    void claimsAnUnclaimedPictureTheCallerUploaded() {
        UUID imageId = UUID.randomUUID();
        StoredImage picture = new StoredImage("k", "image/png", 1, ImagePurpose.DETAIL, me);
        when(storedImages.findById(imageId)).thenReturn(Optional.of(picture));

        service.create(list.getId(), new SaveDataEntryRequest(null, "With a photo", null,
                "<img src=\"/api/images/%s\">".formatted(imageId), null));

        assertThat(picture.getEntryId()).isNotNull();
    }

    @Test
    void doesNotClaimAPictureSomebodyElseUploaded() {
        // An id alone must never drag somebody else's picture onto a
        // collection they can see.
        UUID imageId = UUID.randomUUID();
        AppUser ben = user("ben", UUID.randomUUID());
        StoredImage theirs = new StoredImage("k", "image/png", 1, ImagePurpose.DETAIL, ben);
        when(storedImages.findById(imageId)).thenReturn(Optional.of(theirs));

        service.create(list.getId(), new SaveDataEntryRequest(null, "Not mine", null,
                "<img src=\"/api/images/%s\">".formatted(imageId), null));

        assertThat(theirs.getEntryId()).isNull();
    }

    @Test
    void doesNotClaimAPictureAnItemAlreadyOwns() {
        UUID imageId = UUID.randomUUID();
        StoredImage itemsOwn = new StoredImage("k", "image/png", 1, ImagePurpose.DETAIL, me);
        itemsOwn.claimFor(UUID.randomUUID());
        when(storedImages.findById(imageId)).thenReturn(Optional.of(itemsOwn));

        service.create(list.getId(), new SaveDataEntryRequest(null, "Borrowed", null,
                "<img src=\"/api/images/%s\">".formatted(imageId), null));

        // The database would refuse two owners anyway; this is the 400-shaped
        // version of that rather than a constraint violation.
        assertThat(itemsOwn.getEntryId()).isNull();
    }

    @Test
    void deleteForgetsThePicturesBecauseTheCascadeOnlyTakesTheRows() {
        DataEntry existing = entry("Tiramisu");
        when(entries.findWithList(existing.getId())).thenReturn(Optional.of(existing));
        StoredImage picture = new StoredImage("k", "image/png", 1, ImagePurpose.DETAIL, me);
        when(storedImages.findForEntry(existing.getId())).thenReturn(List.of(picture));

        service.delete(existing.getId(), 0L);

        verify(images).forget(picture);
        verify(entries).delete(existing);
    }

    @Test
    void refusesADeleteWithNoVersion() {
        DataEntry existing = entry("Tiramisu");
        when(entries.findWithList(existing.getId())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.delete(existing.getId(), null))
                .isInstanceOf(StaleDataEntryException.class);
    }

    @Test
    void deletingACollectionForgetsEveryPictureOnIt() {
        StoredImage picture = new StoredImage("k", "image/png", 1, ImagePurpose.DETAIL, me);
        when(storedImages.findForDataList(list.getId())).thenReturn(List.of(picture));

        service.onCollectionDeleted(new DataListDeletedEvent(list.getId()));

        verify(images).forget(picture);
    }

    @Test
    void globalSearchNamesNoCollectionAndSoChecksNoGrantUpFront() {
        // The statement's own EXISTS is the check - which is why it is the
        // driving predicate rather than a filter applied afterwards.
        service.searchEverywhere("dessert", null, null);

        verify(searchQuery).search(org.mockito.ArgumentMatchers.eq(meId),
                org.mockito.ArgumentMatchers.isNull(), any(), any(), any());
    }

    @Test
    void scopedSearchChecksTheGrantFirst() {
        service.list(list.getId(), "dessert", null, null);

        verify(lists).requireReadable(list.getId());
    }
}
