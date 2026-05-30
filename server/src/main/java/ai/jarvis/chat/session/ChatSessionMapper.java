package ai.jarvis.chat.session;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ChatSessionMapper {

    ChatSessionResponse toResponse(ChatSession session);
}
