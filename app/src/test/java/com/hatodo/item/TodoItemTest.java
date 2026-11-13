package com.hatodo.item;

import static org.assertj.core.api.Assertions.assertThat;

import com.hatodo.list.ListIcon;
import com.hatodo.list.TodoList;
import com.hatodo.user.AppUser;
import com.hatodo.user.UserRole;
import org.junit.jupiter.api.Test;

/**
 * What completing, lapsing and reopening do to the row.
 *
 * <p>Worth unit testing rather than leaving to the API suite because R49 made
 * "finished" stop being one column: the completion record survives a reopen,
 * so the only thing that moves is the stamp and the points. What the database
 * refuses - a note on an open item, a reopen before its completion - is
 * asserted against real Postgres in ItemApiIntegrationTest - including the
 * generated `closed` column, which only a real database computes.
 */
class TodoItemTest {

    private final AppUser ana = member("ana");
    private final AppUser ben = member("ben");
    private final TodoList list = new TodoList(ana, "Chores", null, ListIcon.CHECKLIST);

    private static AppUser member(String name) {
        return new AppUser(name, name, null, UserRole.MEMBER);
    }

    private TodoItem item() {
        return new TodoItem(list, "Take the bins out", ana, 0);
    }

    @Test
    void anOpenItemHasNothingToSayAboutCompletion() {
        TodoItem item = item();

        assertThat(item.isCompleted()).isFalse();
        assertThat(item.isLapsed()).isFalse();
        assertThat(item.getState()).isEqualTo(ItemState.OPEN);
        assertThat(item.getCompletedBy()).isNull();
        assertThat(item.getCompletionNoteHtml()).isNull();
        assertThat(item.getReopenedAt()).isNull();
    }

    @Test
    void completingRecordsWhoWhenAndHowItWent() {
        TodoItem item = item();

        item.completeBy(ben, 50, "<p>Bin was full</p>");

        assertThat(item.isCompleted()).isTrue();
        assertThat(item.getCompletedBy()).isEqualTo(ben);
        assertThat(item.getCompletedAt()).isNotNull();
        assertThat(item.getPointsEarned()).isEqualTo(50);
        assertThat(item.getCompletionNoteHtml()).isEqualTo("<p>Bin was full</p>");
    }

    @Test
    void reopeningKeepsTheRecordAndTakesOnlyThePointsBack() {
        // R49, and the reason `closed` had to become a derived column: everything
        // about the completion is still here afterwards (adr/0024).
        TodoItem item = item();
        item.completeBy(ben, 50, "<p>Bin was full</p>");

        item.reopen(ana);

        assertThat(item.isCompleted()).isFalse();
        assertThat(item.getPointsEarned()).isNull();
        assertThat(item.getCompletedBy()).isEqualTo(ben);
        assertThat(item.getCompletedAt()).isNotNull();
        assertThat(item.getCompletionNoteHtml()).isEqualTo("<p>Bin was full</p>");
        assertThat(item.getReopenedBy()).isEqualTo(ana);
        assertThat(item.getReopenedAt()).isNotNull();
    }

    @Test
    void aReopenedItemReportsNoneOfItsOldCompletionOutwards() {
        // Kept in the row, not shown (R49). A completedBy on an item whose
        // state is not COMPLETED would contradict itself.
        TodoItem item = item();
        item.completeBy(ben, 50, "<p>Bin was full</p>");
        item.reopen(ana);

        ItemResponse response = ItemResponse.of(item);

        assertThat(response.state()).isEqualTo(ItemState.OPEN);
        assertThat(response.completedBy()).isNull();
        assertThat(response.completedAt()).isNull();
        assertThat(response.completionNoteHtml()).isNull();
        assertThat(response.reopenedAt()).isNotNull();
    }

    @Test
    void completingAgainClearsTheReopenStampSoTheItemIsFinishedOnceMore() {
        // Without this the generated `closed` column would stay false and the
        // item would read as open while carrying a fresh completion.
        TodoItem item = item();
        item.completeBy(ben, 50, null);
        item.reopen(ana);

        item.completeBy(ana, 50, "<p>Did it properly</p>");

        assertThat(item.isCompleted()).isTrue();
        assertThat(item.getReopenedAt()).isNull();
        assertThat(item.getReopenedBy()).isNull();
        assertThat(item.getCompletedBy()).isEqualTo(ana);
        assertThat(ItemResponse.of(item).completionNoteHtml()).isEqualTo("<p>Did it properly</p>");
    }

    @Test
    void lapsingRecordsWhoAndWhenAndIsWorthNothing() {
        // 0015 R131, R133. The points are the difference that matters: a lapse
        // is terminal without being an achievement.
        TodoItem item = item();

        item.lapseBy(ana);

        assertThat(item.isLapsed()).isTrue();
        assertThat(item.isCompleted()).isFalse();
        assertThat(item.getState()).isEqualTo(ItemState.LAPSED);
        assertThat(item.getLapsedBy()).isEqualTo(ana);
        assertThat(item.getLapsedAt()).isNotNull();
        assertThat(item.getPointsEarned()).isNull();
    }

    @Test
    void aLapsedItemReportsWhoGaveUpOnItAndNoCompletion() {
        TodoItem item = item();
        item.lapseBy(ana);

        ItemResponse response = ItemResponse.of(item);

        assertThat(response.state()).isEqualTo(ItemState.LAPSED);
        assertThat(response.lapsedBy().displayName()).isEqualTo("ana");
        assertThat(response.lapsedAt()).isNotNull();
        assertThat(response.completedBy()).isNull();
        assertThat(response.pointsEarned()).isNull();
    }

    @Test
    void reopeningALapsedItemMakesItWorkAgainAndKeepsTheLapseInTheRow() {
        // 0015 R135. One reopen stamp undoes either terminal state, and keeps
        // its record for the reason adr/0024 keeps a completion.
        TodoItem item = item();
        item.lapseBy(ana);

        item.reopen(ben);

        assertThat(item.isLapsed()).isFalse();
        assertThat(item.getState()).isEqualTo(ItemState.OPEN);
        assertThat(item.getLapsedBy()).isEqualTo(ana);
        assertThat(item.getLapsedAt()).isNotNull();
        assertThat(ItemResponse.of(item).lapsedBy()).isNull();
        assertThat(ItemResponse.of(item).lapsedAt()).isNull();
    }

    @Test
    void lapsingAfterAReopenClearsTheStampAndTakesBackNothingThatWasReturned() {
        // Without clearing the reopen stamp the generated `closed` column would
        // stay false and the chore would sit on the agenda looking open.
        TodoItem item = item();
        item.completeBy(ben, 50, "<p>Bin was full</p>");
        item.reopen(ana);

        item.lapseBy(ana);

        assertThat(item.getState()).isEqualTo(ItemState.LAPSED);
        assertThat(item.getReopenedAt()).isNull();
        assertThat(item.getPointsEarned()).isNull();
    }

    @Test
    void anItemHoldingBothStampsIsLapsedRatherThanCompleted() {
        // The trap 0015 laid, and the reason pointsByMember spells out its
        // predicate rather than reading `closed`: the completion record is
        // still here, deliberately, and this item earned nothing.
        TodoItem item = item();
        item.completeBy(ben, 50, null);
        item.reopen(ana);
        item.lapseBy(ana);

        assertThat(item.isCompleted()).isFalse();
        assertThat(item.getCompletedBy()).isEqualTo(ben);
        assertThat(ItemResponse.of(item).completedBy()).isNull();
    }

    @Test
    void completingALapsedItemClearsTheLapse() {
        // Asymmetric with lapseBy, which keeps the completion record: nothing
        // ever looks back at a lapse the item has moved on from.
        TodoItem item = item();
        item.lapseBy(ana);

        item.completeBy(ben, 50, null);

        assertThat(item.getState()).isEqualTo(ItemState.COMPLETED);
        assertThat(item.getLapsedAt()).isNull();
        assertThat(item.getLapsedBy()).isNull();
        assertThat(item.getPointsEarned()).isEqualTo(50);
    }

    @Test
    void anAssigneeWhoChoseThemselvesIsClaimed() {
        TodoItem item = item();

        item.assignTo(ben, ben);

        assertThat(item.isSelfAssigned()).isTrue();
        assertThat(ItemResponse.of(item).selfAssigned()).isTrue();
    }

    @Test
    void anAssigneeSomebodyElseChoseIsNotClaimed() {
        // The distinction R47 turns on, and the reason assigned_by exists at
        // all: "Ben took this" and "Ana gave this to Ben" are different facts.
        TodoItem item = item();

        item.assignTo(ben, ana);

        assertThat(item.isSelfAssigned()).isFalse();
        assertThat(ItemResponse.of(item).selfAssigned()).isFalse();
    }

    @Test
    void unassigningForgetsWhoChose() {
        TodoItem item = item();
        item.assignTo(ben, ben);

        item.assignTo(null, ana);

        assertThat(item.getAssignee()).isNull();
        assertThat(item.getAssignedBy()).isNull();
        assertThat(item.isSelfAssigned()).isFalse();
    }
}
