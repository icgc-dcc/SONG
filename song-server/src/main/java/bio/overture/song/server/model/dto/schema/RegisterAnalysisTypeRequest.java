package bio.overture.song.server.model.dto.schema;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RegisterAnalysisTypeRequest {
  private String name;
  private JsonNode schema;
}
