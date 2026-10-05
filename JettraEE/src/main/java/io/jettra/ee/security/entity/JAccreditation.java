package io.jettra.ee.security.entity;

import io.jettra.validation.constraints.NotNull;
import io.jettra.validation.constraints.Size;
import java.io.Serializable;
import java.util.UUID;

public record JAccreditation(
        @NotNull
        UUID id,
        @NotNull
        JRole jRole,
        @NotNull
        @Size(min = 1)
        String feature,
        Boolean active
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
