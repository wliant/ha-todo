package com.hatodo.item;

import com.hatodo.list.ListNotFoundException;
import com.hatodo.web.ValidationErrors;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ItemController.class)
class ItemExceptionHandler {

    private final TodoItemService todoItems;

    ItemExceptionHandler(TodoItemService todoItems) {
        this.todoItems = todoItems;
    }

    /**
     * Both the item being gone and its list being invisible, because they are
     * the same answer: asking after the items of a list you hold no grant on
     * must not reveal that the list is real (R19).
     */
    @ExceptionHandler({ItemNotFoundException.class, ListNotFoundException.class})
    ResponseEntity<Map<String, Object>> notFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "That item is no longer here."));
    }

    /**
     * A stale write, from either the explicit precondition or a real
     * concurrent flush - they mean the same thing to the member.
     *
     * <p>The body carries the item as it actually is, so the client reconciles
     * in one round trip instead of guessing. Read in a fresh transaction,
     * because the one that threw is being rolled back; if the item has since
     * been deleted the body simply omits it and the client refetches the list.
     */
    @ExceptionHandler(StaleItemException.class)
    ResponseEntity<Map<String, Object>> stale(StaleItemException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", "STALE_ITEM");
        try {
            ItemResponse current = todoItems.get(ex.getItemId());
            body.put("item", current);
            body.put("error", describe(current));
        } catch (RuntimeException gone) {
            body.put("error", "That item changed while you were looking at it.");
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<Map<String, Object>> lockFailure(ObjectOptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "code", "STALE_ITEM",
                "error", "That item changed while you were looking at it."));
    }

    @ExceptionHandler({MissingRequiredFieldException.class, AssigneeWithoutAccessException.class})
    ResponseEntity<Map<String, Object>> invalid(RuntimeException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> notValid(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ValidationErrors.firstMessage(ex)));
    }

    /** Names the other member, because "a conflict occurred" helps nobody. */
    private static String describe(ItemResponse item) {
        if (item.completed() && item.completedBy() != null) {
            return "%s completed this first.".formatted(item.completedBy().displayName());
        }
        return "Somebody else changed this item while you were looking at it.";
    }
}
