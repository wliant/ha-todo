package com.hatodo.list;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * The refusals, and the sentences they carry.
 *
 * <p>The message is the feature here: the member is about to read it, and
 * "invalid configuration" tells them nothing about which of several costs is
 * wrong or why. What the database refuses, and the merge that has to preserve
 * ids, are asserted against real Postgres in ListFieldApiIntegrationTest.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ListFieldServiceTest {

    @Mock private ListFieldRepository fields;
    @Mock private TodoListService lists;

    private final UUID listId = UUID.randomUUID();
    private ListFieldService service;

    @BeforeEach
    void setUp() {
        when(fields.findForList(any())).thenReturn(List.of());
        when(fields.save(any())).thenAnswer(call -> call.getArgument(0));
        service = new ListFieldService(fields, lists);
    }

    private void replaceWith(ListFieldRequest... requested) {
        service.replace(listId, new UpdateFieldsRequest(0L, List.of(requested)));
    }

    private static ListFieldRequest cost(String label, CostMode mode, String unit,
                                         CostChoiceRequest... choices) {
        return new ListFieldRequest(null, FieldKind.COST, label, false, mode, unit, null,
                List.of(choices));
    }

    @Test
    void namesTheCostThatHasNothingToPickFrom() {
        assertThatThrownBy(() -> replaceWith(cost("Time", CostMode.FIXED, null)))
                .isInstanceOf(InvalidFieldConfigurationException.class)
                .hasMessageContaining("Time")
                .hasMessageContaining("at least one choice");
    }

    @Test
    void namesTheCostThatIsNotMeasuredInAnything() {
        assertThatThrownBy(() -> replaceWith(cost("Money", CostMode.NUMERIC, "  ")))
                .isInstanceOf(InvalidFieldConfigurationException.class)
                .hasMessageContaining("Money");
    }

    @Test
    void saysWhichCostNameIsDuplicated() {
        // Matched case-insensitively, because the unique index is on
        // lower(label) and a difference in capitals is not a different cost.
        // The message quotes what was typed, which is what the member is
        // looking at.
        assertThatThrownBy(() -> replaceWith(
                cost("Time", CostMode.NUMERIC, "hour"),
                cost("time", CostMode.NUMERIC, "hour")))
                .isInstanceOf(InvalidFieldConfigurationException.class)
                .hasMessageContaining("time");
    }

    @Test
    void refusesACostWithNoNameAtAll() {
        assertThatThrownBy(() -> replaceWith(cost("   ", CostMode.NUMERIC, "hour")))
                .isInstanceOf(InvalidFieldConfigurationException.class)
                .hasMessageContaining("needs a name");
    }

    @Test
    void refusesACostThatDoesNotSayWhichKindItIs() {
        assertThatThrownBy(() -> replaceWith(cost("Time", null, null)))
                .isInstanceOf(InvalidFieldConfigurationException.class)
                .hasMessageContaining("choices or a number");
    }

    @Test
    void refusesTwoChoicesOnOneCostWithTheSameName() {
        assertThatThrownBy(() -> replaceWith(cost("Time", CostMode.FIXED, null,
                new CostChoiceRequest(null, "30 min", 10),
                new CostChoiceRequest(null, "30 MIN", 20))))
                .isInstanceOf(InvalidFieldConfigurationException.class)
                .hasMessageContaining("Time")
                .hasMessageContaining("30 MIN");
    }

    @Test
    void namesTheSingletonFieldThatWasAskedForTwice() {
        assertThatThrownBy(() -> replaceWith(
                new ListFieldRequest(null, FieldKind.ASSIGNEE, null, true, null, null, null, null),
                new ListFieldRequest(null, FieldKind.ASSIGNEE, null, false, null, null, null, null)))
                .isInstanceOf(InvalidFieldConfigurationException.class)
                .hasMessageContaining("assignee");
    }

    @Test
    void refusesToReinterpretAnExistingFieldAsADifferentKind() {
        // Whatever items already answered it would silently change meaning.
        UUID fieldId = UUID.randomUUID();
        TodoList list = new TodoList(null, "List", null, ListIcon.CHECKLIST);
        ListField existing = new ListField(list, FieldKind.DUE_AT, null, false, (short) 1);
        org.springframework.test.util.ReflectionTestUtils.setField(existing, "id", fieldId);
        when(fields.findForList(listId)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> replaceWith(
                new ListFieldRequest(fieldId, FieldKind.ASSIGNEE, null, false, null, null, null, null)))
                .isInstanceOf(InvalidFieldConfigurationException.class)
                .hasMessageContaining("cannot change from one kind to another");
    }

    @Test
    void alwaysDeclaresADescriptionEvenWhenNobodyAsksForOne() {
        List<ListFieldResponse> result = service.replace(listId,
                new UpdateFieldsRequest(0L, List.of(
                        new ListFieldRequest(null, FieldKind.DUE_AT, null, false, null, null, null, null))));

        assertThat(result).extracting(ListFieldResponse::kind)
                .containsExactly(FieldKind.DESCRIPTION, FieldKind.DUE_AT);
        assertThat(result.get(0).required()).isTrue();
    }
}
