package com.kesh.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ByteArrayResource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kesh.config.KeshProperties;
import com.kesh.config.SystemPromptProvider;
import com.kesh.persistence.ConversationArchive;
import com.kesh.persistence.DeskPreferenceStore;
import com.kesh.tools.FakeTool;
import com.kesh.tools.PermissionLevel;
import com.kesh.tools.ToolRegistry;
import com.kesh.workspace.WorkspaceMode;

class ConversationStoreIsolationTest {

    @TempDir
    Path temp;

    @Test
    void workChatIsNotVisibleOnPersonalDesk() {
        ConversationStore store = store();
        ConversationContext work = store.getOrCreate(WorkspaceMode.PROFESSIONAL, "same-id");
        work.addUser("jira ticket HS-1");
        work.addAssistant("Looking at Jira.");
        store.save(work);

        assertThat(store.find(WorkspaceMode.PERSONAL, "same-id")).isNull();
        assertThatThrownBy(() -> store.require(WorkspaceMode.PERSONAL, "same-id"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(store.recent(WorkspaceMode.PERSONAL)).isEmpty();
        assertThat(store.recent(WorkspaceMode.PROFESSIONAL)).extracting(summary -> summary.id())
                .containsExactly("same-id");
    }

    @Test
    void dictionaryAndWorkToolsAreAvailableOnEveryDesk() {
        assertThat(WorkspaceMode.PERSONAL.allowsTool("dictionary_lookup")).isTrue();
        assertThat(WorkspaceMode.PROFESSIONAL.allowsTool("dictionary_lookup")).isTrue();
        assertThat(WorkspaceMode.PERSONAL.allowsTool("get_unread_slack_messages")).isTrue();
        assertThat(WorkspaceMode.PROFESSIONAL.allowsTool("get_unread_slack_messages")).isTrue();
    }

    private ConversationStore store() {
        KeshProperties properties = new KeshProperties();
        properties.getWorkspace().setDataDir(temp.toString());
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ToolRegistry registry = new ToolRegistry(
                List.of(new FakeTool("current_time", PermissionLevel.READ)),
                new SystemPromptProvider(new ByteArrayResource("You are Kesh.".getBytes()))
        );
        return new ConversationStore(
                registry,
                new ConversationArchive(properties, mapper),
                new DeskPreferenceStore(properties, mapper)
        );
    }
}
