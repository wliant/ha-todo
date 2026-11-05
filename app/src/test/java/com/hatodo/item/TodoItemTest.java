package com.hatodo.item;

import static org.assertj.core.api.Assertions.assertThat;

import com.hatodo.list.ListIcon;
import com.hatodo.list.TodoList;
import com.hatodo.user.AppUser;
import com.hatodo.user.UserRole;
import org.junit.jupiter.api.Test;

/**
 * What completing and reopening do to the row.
 *
 * <p>Worth unit testing rather than leaving to the API suite because R49 made
 * "finished" stop being one column: the completion record survives a reopen,
 * so the only thing that moves is the stamp and the points. What the database
 * refuses - a note on an open item, a reopen before its completion - is
 * asserted against real Postgres in ItemApiIntegrationTest.
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
        // R49, and the reason `done` had to become a derived column: everything
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
        // `completed` is false would contradict itself.
        TodoItem item = item();
        item.completeBy(ben, 50, "<p>Bin was full</p>");
        item.reopen(ana);

        ItemResponse response = ItemResponse.of(item);

        assertThat(response.completed()).isFalse();
        assertThat(response.completedBy()).isNull();
        assertThat(response.completedAt()).isNull();
        assertThat(response.completionNoteHtml()).isNull();
        assertThat(response.reopenedAt()).isNotNull();
    }

    @Test
    void completingAgainClearsTheReopenStampSoTheItemIsFinishedOnceMore() {
        // Without this the generated `done` column would stay false and the
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
