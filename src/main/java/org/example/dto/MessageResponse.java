package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A plain acknowledgement, for endpoints with nothing else to return. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {

    private String message;
}
