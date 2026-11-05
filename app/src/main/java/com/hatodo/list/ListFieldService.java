package com.hatodo.list;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The shape a list's items take (R21-R23).
 *
 * <p>The configuration is <em>replaced</em>, but by merging on id rather than
 * by deleting and reinserting. Item cost values reference a field and a choice
 * by id, so recreating the rows would orphan every item's costs - a data-loss
 * bug rather than a tidiness one. Clients therefore send existing ids back, and
 * anything without one is new. See adr/0011.
 */
@Service
public class ListFieldService {

    private final ListFieldRepository fields;
    private final TodoListService lists;

    public ListFieldService(ListFieldRepository fields, TodoListService lists) {
        this.fields = fields;
        this.lists = lists;
    }

    @Transactional(readOnly = true)
    public List<ListFieldResponse> get(UUID listId) {
        lists.requireReadable(listId);
        return fields.findForList(listId).stream().map(ListFieldResponse::of).toList();
    }

    @Transactional
    public List<ListFieldResponse> replace(UUID listId, UpdateFieldsRequest request) {
        TodoList list = lists.requireManagedAndFresh(listId, request.version());

        List<ListFieldRequest> wanted = normalise(request.fields());
        validate(wanted);

        Map<UUID, ListField> existing = new HashMap<>();
        Map<FieldKind, ListField> singletons = new HashMap<>();
        for (ListField field : fields.findForList(listId)) {
            existing.put(field.getId(), field);
            if (field.getKind().isSingleton()) {
                singletons.put(field.getKind(), field);
            }
        }

        List<ListField> result = new ArrayList<>();
        short position = 0;
        Set<UUID> kept = new HashSet<>();

        for (ListFieldRequest desired : wanted) {
            // A singleton kind is identified by its kind - there can only be
            // one - so a client that does not track its id still updates the
            // existing row rather than colliding with it. Cost fields have no
            // such natural identity and must send theirs back.
            ListField field = desired.id() != null
                    ? existing.get(desired.id())
                    : (desired.kind().isSingleton() ? singletons.get(desired.kind()) : null);
            if (field == null) {
                field = new ListField(list, desired.kind(), null, desired.isRequired(), position);
            } else if (field.getKind() != desired.kind()) {
                // Changing a field's kind in place would silently reinterpret
                // whatever items already answered it.
                throw new InvalidFieldConfigurationException(
                        "A field cannot change from one kind to another.");
            }

            field.setRequired(desired.kind() == FieldKind.DESCRIPTION || desired.isRequired());
            field.setPosition(position++);
            if (desired.kind() == FieldKind.COST) {
                field.setLabel(desired.label().trim());
                applyCost(field, desired);
            }

            result.add(fields.save(field));
            if (field.getId() != null) {
                kept.add(field.getId());
            }
        }

        for (ListField orphan : existing.values()) {
            if (!kept.contains(orphan.getId())) {
                fields.delete(orphan);
            }
        }

        // The fields are children of the list, so changing them has to move the
        // list's version or a second editor is never told their view is stale.
        lists.markChanged(list);
        return result.stream().map(ListFieldResponse::of).toList();
    }

    // --- validation ---------------------------------------------------------

    /**
     * Puts the always-declared fields first and guarantees they are there, so
     * the rest of the rules can be about what the member actually chose.
     *
     * <p>Description has always been one of these. The due date joined it for
     * R40: a dashboard reading across lists can only answer from the lists that
     * declared the field, so the field is not removable - though it stays
     * optional to fill in, and the owner may still make it required. A request
     * that omits it gets it back rather than an error, because "not mentioned"
     * already means "unchanged" for a singleton kind. See adr/0022.
     *
     * <p>Presence is guaranteed here rather than by a constraint because SQL
     * cannot say "every row in this table has a child of this kind" without a
     * trigger - the same position adr/0011 takes for the description.
     */
    private List<ListFieldRequest> normalise(List<ListFieldRequest> requested) {
        List<ListFieldRequest> ordered = new ArrayList<>();
        ordered.add(requested.stream()
                .filter(field -> field.kind() == FieldKind.DESCRIPTION)
                .findFirst()
                .orElse(new ListFieldRequest(null, FieldKind.DESCRIPTION, null, Boolean.TRUE,
                        null, null, null, null)));
        requested.stream()
                .filter(field -> field.kind() != FieldKind.DESCRIPTION)
                .forEach(ordered::add);

        // Guaranteed present, not pinned to a position: a caller who sent it
        // keeps it where they put it, and a list can still reorder it. Only
        // omitting it is impossible, and then it goes on the end rather than
        // shifting whatever the caller did send.
        if (ordered.stream().noneMatch(field -> field.kind() == FieldKind.DUE_AT)) {
            ordered.add(new ListFieldRequest(null, FieldKind.DUE_AT, null, Boolean.FALSE,
                    null, null, null, null));
        }
        return ordered;
    }

    private void validate(List<ListFieldRequest> wanted) {
        Set<FieldKind> singletons = new LinkedHashSet<>();
        Set<String> costLabels = new LinkedHashSet<>();

        for (ListFieldRequest field : wanted) {
            if (field.kind().isSingleton() && !singletons.add(field.kind())) {
                throw new InvalidFieldConfigurationException(
                        "A list can only ask for " + describe(field.kind()) + " once.");
            }
            if (field.kind() != FieldKind.COST) {
                continue;
            }

            String label = field.label() == null ? "" : field.label().trim();
            if (label.isEmpty()) {
                throw new InvalidFieldConfigurationException("Every cost needs a name.");
            }
            if (!costLabels.add(label.toLowerCase(Locale.ROOT))) {
                throw new InvalidFieldConfigurationException(
                        "Two costs cannot both be called " + label + ".");
            }
            if (field.costMode() == null) {
                throw new InvalidFieldConfigurationException(
                        "Say whether " + label + " is a set of choices or a number.");
            }
            if (field.costMode() == CostMode.FIXED) {
                validateFixed(label, field);
            } else {
                validateNumeric(label, field);
            }
        }
    }

    private void validateFixed(String label, ListFieldRequest field) {
        List<CostChoiceRequest> choices = field.choicesOrEmpty();
        if (choices.isEmpty()) {
            // The one invariant SQL cannot hold without a trigger, and a
            // trigger is a worse home for a rule the API already rejects with
            // a sentence somebody can read.
            throw new InvalidFieldConfigurationException(
                    label + " needs at least one choice to pick from.");
        }
        Set<String> labels = new LinkedHashSet<>();
        for (CostChoiceRequest choice : choices) {
            String choiceLabel = choice.label() == null ? "" : choice.label().trim();
            if (choiceLabel.isEmpty()) {
                throw new InvalidFieldConfigurationException("Every choice on " + label + " needs a name.");
            }
            if (!labels.add(choiceLabel.toLowerCase(Locale.ROOT))) {
                throw new InvalidFieldConfigurationException(
                        label + " has two choices called " + choiceLabel + ".");
            }
        }
    }

    private void validateNumeric(String label, ListFieldRequest field) {
        String unit = field.costUnitLabel() == null ? "" : field.costUnitLabel().trim();
        if (unit.isEmpty()) {
            throw new InvalidFieldConfigurationException(
                    "Say what " + label + " is measured in - dollars, hours, kilometres.");
        }
    }

    private void applyCost(ListField field, ListFieldRequest desired) {
        if (desired.costMode() == CostMode.NUMERIC) {
            field.configureNumericCost(desired.costUnitLabel().trim(), desired.pointsPerUnit());
            return;
        }

        field.configureFixedCost();
        Map<UUID, ListFieldChoice> existing = new HashMap<>();
        for (ListFieldChoice choice : field.getChoices()) {
            existing.put(choice.getId(), choice);
        }

        List<ListFieldChoice> merged = new ArrayList<>();
        short position = 0;
        for (CostChoiceRequest desiredChoice : desired.choicesOrEmpty()) {
            ListFieldChoice choice =
                    desiredChoice.id() == null ? null : existing.get(desiredChoice.id());
            if (choice == null) {
                choice = new ListFieldChoice(field, desiredChoice.label().trim(),
                        desiredChoice.pointsOrZero(), position);
            } else {
                choice.setLabel(desiredChoice.label().trim());
                choice.setPoints(desiredChoice.pointsOrZero());
                choice.setPosition(position);
            }
            position++;
            merged.add(choice);
        }

        // orphanRemoval deletes whatever is no longer in the collection; the
        // positions are deferred, so rewriting them all in one go is fine.
        field.getChoices().clear();
        field.getChoices().addAll(merged);
    }

    private static String describe(FieldKind kind) {
        return switch (kind) {
            case DESCRIPTION -> "a description";
            case DETAIL -> "detail";
            case ASSIGNEE -> "an assignee";
            case DUE_AT -> "a due date";
            case COST -> "a cost";
        };
    }
}
