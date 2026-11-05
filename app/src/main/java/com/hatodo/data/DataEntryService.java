package com.hatodo.data;

import com.hatodo.images.ImageService;
import com.hatodo.images.StoredImage;
import com.hatodo.images.StoredImageRepository;
import com.hatodo.richtext.DetailImages;
import com.hatodo.richtext.HtmlSanitizer;
import com.hatodo.richtext.HtmlText;
import com.hatodo.user.AppUser;
import com.hatodo.user.AppUserRepository;
import com.hatodo.user.CurrentUserProvider;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Entries, and the one rule about who may touch them: anybody who can see the
 * collection (requirements/0006 R60).
 *
 * <p>That is {@code TodoItemService}'s rule verbatim - add, edit and delete are
 * all checked with {@code readable}, never {@code managed}. Ownership is about
 * the collection's shape, not its contents.
 */
@Service
public class DataEntryService {

    private final DataEntryRepository entries;
    private final DataListService lists;
    private final DataListRepository dataLists;
    private final AppUserRepository users;
    private final CurrentUserProvider currentUser;
    private final HtmlSanitizer sanitizer;
    private final HtmlText htmlText;
    private final DetailImages detailImages;
    private final TagNormaliser tags;
    private final StoredImageRepository storedImages;
    private final ImageService images;
    private final DataEntrySearchQuery searchQuery;
    private final DataSearchQueryText queryText;

    public DataEntryService(DataEntryRepository entries, DataListService lists,
                            DataListRepository dataLists, AppUserRepository users,
                            CurrentUserProvider currentUser, HtmlSanitizer sanitizer,
                            HtmlText htmlText, DetailImages detailImages, TagNormaliser tags,
                            StoredImageRepository storedImages, ImageService images,
                            DataEntrySearchQuery searchQuery, DataSearchQueryText queryText) {
        this.entries = entries;
        this.lists = lists;
        this.dataLists = dataLists;
        this.users = users;
        this.currentUser = currentUser;
        this.sanitizer = sanitizer;
        this.htmlText = htmlText;
        this.detailImages = detailImages;
        this.tags = tags;
        this.storedImages = storedImages;
        this.images = images;
        this.searchQuery = searchQuery;
        this.queryText = queryText;
    }

    /**
     * One collection's entries, or a search within it.
     *
     * <p>Scoped search and a plain listing are the same call, because they are
     * the same statement with a null query - and because the client's search
     * field should not change which endpoint it talks to as the member types.
     */
    @Transactional(readOnly = true)
    public List<DataEntryHit> list(UUID listId, String query, List<String> tagFilter,
                                   Integer limit) {
        lists.requireReadable(listId);
        return search(listId, query, tagFilter, limit);
    }

    /**
     * Across every collection the caller can see (R54).
     *
     * <p>No collection is named, so there is nothing to grant-check up front:
     * the statement's own {@code EXISTS} on {@code data_list_access} is the
     * check, which is why it is the driving predicate rather than a filter
     * applied afterwards.
     */
    @Transactional(readOnly = true)
    public List<DataEntryHit> searchEverywhere(String query, List<String> tagFilter,
                                               Integer limit) {
        return search(null, query, tagFilter, limit);
    }

    private List<DataEntryHit> search(UUID listId, String query, List<String> tagFilter,
                                      Integer limit) {
        UUID me = currentUser.requireUserId();
        return searchQuery.search(me, listId, queryText.parse(query), tagFilter, limit);
    }

    @Transactional(readOnly = true)
    public List<TagSuggestionResponse> tagSuggestions(UUID listId, String prefix) {
        lists.requireReadable(listId);
        return searchQuery.tagSuggestions(listId, prefix);
    }

    @Transactional(readOnly = true)
    public DataEntryResponse get(UUID entryId) {
        return DataEntryResponse.of(readable(entryId));
    }

    @Transactional
    public DataEntryResponse create(UUID listId, SaveDataEntryRequest request) {
        AppUser me = requireCurrentUser();
        DataList list = lists.requireReadable(listId);

        DataEntry entry = new DataEntry(list, request.name().trim(), me);
        apply(entry, request);
        DataEntry saved = entries.save(entry);
        claimImages(saved, me.getId());
        return DataEntryResponse.of(saved);
    }

    @Transactional
    public DataEntryResponse update(UUID entryId, SaveDataEntryRequest request) {
        AppUser me = requireCurrentUser();
        DataEntry entry = readable(entryId);
        // Before anything is mutated, so the conflict is deterministic rather
        // than dependent on when Hibernate flushes.
        requireFreshView(entry, request.version());

        entry.setName(request.name().trim());
        apply(entry, request);
        claimImages(entry, me.getId());
        return DataEntryResponse.of(entry);
    }

    @Transactional
    public void delete(UUID entryId, Long version) {
        DataEntry entry = readable(entryId);
        requireFreshView(entry, version);
        forgetPicturesOf(entry.getId());
        entries.delete(entry);
    }

    /**
     * Lets go of the bytes behind an entry's pictures. The rows go with the
     * entry by cascade; these do not, so they are forgotten explicitly.
     */
    @Transactional
    public void forgetPicturesOf(UUID entryId) {
        for (StoredImage image : storedImages.findForEntry(entryId)) {
            images.forget(image);
        }
    }

    /**
     * Every picture on a collection that is about to be deleted.
     *
     * <p>Listens rather than being called, so {@link DataListService} does not
     * have to know that entries own pictures - the same shape
     * {@code ListEventListener} already uses for lists.
     */
    @EventListener
    @Transactional
    public void onCollectionDeleted(DataListDeletedEvent event) {
        for (StoredImage image : storedImages.findForDataList(event.listId())) {
            images.forget(image);
        }
    }

    private void apply(DataEntry entry, SaveDataEntryRequest request) {
        entry.setDescription(trimToNull(request.description()));

        // Sanitized on write (adr/0018), and the plain text derived from what
        // survived rather than from what arrived - indexing the input would
        // index text the member never gets to see.
        String html = sanitizer.sanitize(request.detailHtml());
        entry.setDetail(html, htmlText.toPlainText(html));

        entry.setTags(tags.normalise(request.tagsOrEmpty()));
    }

    /**
     * Only pictures this member uploaded, and only ones nothing else owns.
     *
     * <p>An id alone must never drag somebody else's picture onto a collection
     * - that is the whole reason the claim is checked rather than inferred from
     * the HTML.
     */
    private void claimImages(DataEntry entry, UUID uploaderId) {
        Set<UUID> referenced = detailImages.referencedIn(entry.getDetailHtml());
        for (UUID imageId : referenced) {
            storedImages.findById(imageId)
                    // Objects.equals rather than entry.getId().equals: the
                    // id is assigned by the time this runs, and an NPE here
                    // would be a 500 on a save that was otherwise fine.
                    .filter(image -> image.isUnclaimed()
                            || java.util.Objects.equals(entry.getId(), image.getEntryId()))
                    .filter(image -> image.wasUploadedBy(uploaderId))
                    .ifPresent(image -> image.claimForEntry(entry.getId()));
        }
    }

    /**
     * An entry on a collection the caller can see.
     *
     * <p>An entry on a collection they cannot see answers exactly as a deleted
     * one does: which collection it is on is itself something R50 hides.
     */
    private DataEntry readable(UUID entryId) {
        DataEntry entry = entries.findWithList(entryId)
                .orElseThrow(() -> new DataEntryNotFoundException(entryId));
        if (!lists.isReadable(entry.getListId())) {
            throw new DataEntryNotFoundException(entryId);
        }
        return entry;
    }

    private void requireFreshView(DataEntry entry, Long seenVersion) {
        if (seenVersion == null || entry.getVersion() != seenVersion) {
            throw new StaleDataEntryException(entry.getId());
        }
    }

    /**
     * Re-reads an entry in a transaction of its own, for the conflict
     * response.
     *
     * <p>{@code REQUIRES_NEW} because the transaction that threw is marked
     * rollback-only, and reading through it would fail on commit - so the 409
     * would become a 500 on exactly the path that exists to avoid one.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW,
            readOnly = true)
    public DataEntryResponse readFresh(UUID entryId) {
        return entries.findWithList(entryId).map(DataEntryResponse::of).orElse(null);
    }

    private AppUser requireCurrentUser() {
        UUID id = currentUser.requireUserId();
        return users.findById(id).orElseThrow(
                () -> new IllegalStateException("Authenticated user " + id + " no longer exists"));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
