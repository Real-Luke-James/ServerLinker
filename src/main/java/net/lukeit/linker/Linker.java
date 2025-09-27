package net.lukeit.linker;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.geysermc.floodgate.api.FloodgateApi;

import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.util.List;

import static net.minecraft.server.command.CommandManager.literal;

public class Linker implements ModInitializer {

    private static final Gson GSON = new Gson();
    private static CommandConfig config;

    @Override
    public void onInitialize() {
        loadConfig();
        CommandRegistrationCallback.EVENT.register(this::onRegisterCommands);
    }

    private void onRegisterCommands(CommandDispatcher<ServerCommandSource> dispatcher,
                                    CommandRegistryAccess commandRegistryAccess,
                                    CommandManager.RegistrationEnvironment registrationEnvironment) {

        if (config == null || config.commands == null) return;

        for (CommandEntry entry : config.commands) {
            String commandName = entry.name;

            LiteralArgumentBuilder<ServerCommandSource> cmd = literal(commandName)
                    .executes(ctx -> executeTransfer(ctx, entry.ip, entry.port));

            dispatcher.register(cmd);
        }
    }

    private int executeTransfer(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx,
                                String ipArgument,
                                Integer portArgument) {
        ServerCommandSource src = ctx.getSource();

        if (!(src.getEntity() instanceof ServerPlayerEntity player)) {
            src.sendError(Text.literal("[Linker] Only players can run this command."));
            return 0;
        }

        boolean isBedrock;
        try {
            isBedrock = FloodgateApi.getInstance().isFloodgatePlayer(player.getUuid());
        } catch (Throwable t) {
            src.sendError(Text.literal("[Linker] Floodgate API not available."));
            return 0;
        }

        // Use JSON IP/port if provided, otherwise defaults
        String targetIp = ipArgument != null ? ipArgument : (isBedrock ? "bedrock.example.com" : "java.example.com");
        int targetPort = portArgument != null ? portArgument : (isBedrock ? 19132 : 25565);

        String command = String.format("transfer %s %d", targetIp, targetPort);

        try {
            int result = src.getServer().getCommandManager().getDispatcher().execute(command, src);
            if (result == 0) {
                src.sendFeedback(() -> Text.literal("[Linker] Transfer command returned 0: " + command), false);
            }
            return result;
        } catch (CommandSyntaxException e) {
            src.sendError(Text.literal("[Linker] Syntax error: " + e.getMessage()));
        } catch (Exception e) {
            src.sendError(Text.literal("[Linker] Unexpected error. See server log."));
            e.printStackTrace();
        }

        return 0;
    }

    private void loadConfig() {
        try {
            File configDir = new File("config/linker");
            if (!configDir.exists()) configDir.mkdirs();
            File configFile = new File(configDir, "commands.json");

            if (!configFile.exists()) {
                configFile.createNewFile();
                return;
            }

            try (FileReader reader = new FileReader(configFile)) {
                Type type = new TypeToken<CommandConfig>() {}.getType();
                config = GSON.fromJson(reader, type);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class CommandConfig {
        public List<CommandEntry> commands;
    }

    public static class CommandEntry {
        public String name;    // Command name
        public String ip;      // Target server IP
        public Integer port;   // Target server port
    }
}
