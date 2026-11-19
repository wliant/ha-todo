package com.hatodo.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hatodo.item.ItemCreatedEvent;
import com.hatodo.list.ListAccessRepository;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Who hears about a new chore, and what they are told about who added it.
 */
@ExtendWith(MockitoExtension.class)
class ItemCreatedListenerTest {

    private static final UUID ANA = UUID.randomUUID();
    private static final UUID BEN = UUID.randomUUID();
    private static final UUID CARA = UUID.randomUUID();
    private static final UUID LIST = UUID.randomUUID();
    private static final UUID ITEM = UUID.randomUUID();

    @Mock
    private ListAccessRepository access;

    @Mock
    private LiveBroker broker;

    @InjectMocks
    private ItemCreatedListener listener;

    @Test
    void tellsEverybodyWithAGrantAndNobodyElse() {
        granted(ANA, BEN);

        listener.onItemCreated(addedBy(ANA));

        // Cara holds no grant on this list. She is not in the map, so there is
        // no stream of hers the broker could write to - the routing decision
        // adr/0055 exists for.
        assertThat(delivered().keySet()).containsExactlyInAnyOrder(ANA, BEN);
    }

    @Test
    void staysQuietAboutTheMembersOwnAddition() {
        granted(ANA, BEN);

        listener.onItemCreated(addedBy(ANA));

        // Still delivered to Ana - she may have a second device open, and that
        // one needs the refresh (R181). Only the naming is suppressed.
        assertThat(delivered().get(ANA).byYou()).isTrue();
        assertThat(delivered().get(BEN).byYou()).isFalse();
    }

    @Test
    void namesWhoeverAddedIt() {
        granted(ANA, BEN);

        listener.onItemCreated(addedBy(ANA));

        assertThat(delivered().get(BEN).actorDisplayName()).isEqualTo("Ana");
        assertThat(delivered().get(BEN).description()).isEqualTo("Milk");
        assertThat(delivered().get(BEN).listName()).isEqualTo("Groceries");
    }

    /**
     * R182, and the one most easily lost. {@code createFromRule} takes the
     * rule's author as its actor - a real person, deliberately - so without the
     * RECURRENCE discriminator the member who wrote the standing instruction
     * would be the one person never told what it created at 01:05. Exactly
     * backwards, and nothing else would fail.
     */
    @Test
    void theRuleSAuthorIsStillToldWhatTheirRuleCreated() {
        granted(ANA, BEN);

        listener.onItemCreated(generatedFromARuleWrittenBy(ANA));

        assertThat(delivered().get(ANA).byYou()).isFalse();
        assertThat(delivered().get(BEN).byYou()).isFalse();
    }

    @Test
    void namesNobodyWhenTheJobAddedIt() {
        granted(ANA);

        listener.onItemCreated(generatedFromARuleWrittenBy(ANA));

        // Nobody added this, so nobody is named: the client words it
        // "added automatically" rather than attributing it to the author.
        assertThat(delivered().get(ANA).actorDisplayName()).isNull();
    }

    @Test
    void aListNobodyCanSeeNotifiesNobody() {
        when(access.findUserIdsForList(LIST)).thenReturn(List.of());

        listener.onItemCreated(addedBy(ANA));

        assertThat(delivered()).isEmpty();
    }

    @Test
    void aFailureToNotifyNeverFailsTheCreate() {
        granted(ANA);
        doThrow(new IllegalStateException("writer is gone")).when(broker).send(any());

        listener.onItemCreated(addedBy(ANA));

        // No throw. This runs on the publishing thread after the commit, so a
        // throw here would turn a successful create into a 500 on a row that
        // was saved.
        verify(broker).send(any());
    }

    @Test
    void aDatabaseFailureNeverFailsTheCreateEither() {
        when(access.findUserIdsForList(LIST)).thenThrow(new IllegalStateException("gone"));

        listener.onItemCreated(addedBy(ANA));

        verify(broker, never()).send(any());
    }

    /**
     * The one defect in this feature that is invisible at runtime: notifying
     * before the commit tells a browser to refetch a row Postgres has not made
     * visible, and it self-heals on the next refetch. Nothing else could fail,
     * so this asserts the annotation itself.
     */
    @Test
    void notifiesOnlyAfterTheTransactionCommits() throws NoSuchMethodException {
        Method method = ItemCreatedListener.class
                .getMethod("onItemCreated", ItemCreatedEvent.class);

        TransactionalEventListener annotation =
                method.getAnnotation(TransactionalEventListener.class);

        assertThat(annotation).as("a plain @EventListener would notify before the commit")
                .isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    private void granted(UUID... members) {
        when(access.findUserIdsForList(LIST)).thenReturn(List.of(members));
    }

    private Map<UUID, LiveNotification> delivered() {
        ArgumentCaptor<Map<UUID, LiveNotification>> captor = ArgumentCaptor.captor();
        verify(broker).send(captor.capture());
        return captor.getValue();
    }

    private ItemCreatedEvent addedBy(UUID actor) {
        return new ItemCreatedEvent(ITEM, LIST, "Groceries", "Milk", actor, "Ana",
                ItemCreatedEvent.Source.MEMBER);
    }

    private ItemCreatedEvent generatedFromARuleWrittenBy(UUID author) {
        return new ItemCreatedEvent(ITEM, LIST, "Groceries", "Take the bins out", author, "Ana",
                ItemCreatedEvent.Source.RECURRENCE);
    }
}
