package io.jettra.ee.security.entity;

import io.jettra.validation.constraints.NotNull;
import io.jettra.validation.constraints.Size;
import java.io.Serializable;
import java.util.UUID;

public record JRole(
        @NotNull
        UUID id,
        @NotNull
        @Size(min = 2)
        String name,
        @NotNull
        Boolean active
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
