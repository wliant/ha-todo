package com.hatodo.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

class ValidationErrorsTest {

    private record Form(String username) {
    }

    private static MethodArgumentNotValidException failure(String field, String message) {
        BindingResult binding = new BeanPropertyBindingResult(new Form("x"), "form");
        if (field != null) {
            binding.rejectValue(field, "code", message);
        }
        return new MethodArgumentNotValidException(null, binding);
    }

    @Test
    void prefixesAFragmentWithTheFieldItCameFrom() {
        assertThat(ValidationErrors.firstMessage(failure("username", "must not be blank")))
                .isEqualTo("username must not be blank");
    }

    @Test
    void usesAMessageThatAlreadyReadsAsASentenceAsItIs() {
        assertThat(ValidationErrors.firstMessage(
                failure("username", "May contain only letters, digits, dot, underscore and hyphen")))
                .isEqualTo("May contain only letters, digits, dot, underscore and hyphen");
    }

    @Test
    void fallsBackWhenThereIsNoFieldErrorToReport() {
        assertThat(ValidationErrors.firstMessage(failure(null, null)))
                .isEqualTo("That request was not valid.");
    }
}
