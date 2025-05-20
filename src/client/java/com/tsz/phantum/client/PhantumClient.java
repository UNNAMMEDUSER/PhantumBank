package com.tsz.phantum.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class PhantumClient implements ClientModInitializer {
    private static boolean LOG = false;

    private static final ExecutorService HTTP_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Pattern NICKNAME_PATTERN = Pattern.compile("<div class=id>\\s*(\\S+?)\\s*<\\/div>");
    private static final Pattern STATUS_PATTERN = Pattern.compile("<div class=status>(.*?)<\\/div>");
    private static final Pattern NAME_PATTERN = Pattern.compile("<div class=name>(.*?)<\\/div>");



    private static final Pattern RECIVER_ID_PATTERN = Pattern.compile("<div class=reciver_id>\\s*(\\S+?)\\s*<\\/div>");
    private static final Pattern SENDER_ID_PATTERN = Pattern.compile("<div class=sender_id>\\s*(\\S+?)\\s*<\\/div>");
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("<div class=amount>\\s*(\\S+?)\\s*<\\/div>");
    private static final Pattern ID_PATTERN = Pattern.compile("<div class=id>\\s*(\\S+?)\\s*<\\/div>");
    private static final Pattern DATE_PATTERN = Pattern.compile("<div class=date>\\s*(\\d{4}-\\d{2}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2})\\s*<\\/div>");


    private static String usernameg = "";
    private static String passwordg = "";
    private static String requestBody = ("nickname=" + usernameg + "&password=" + passwordg);
    private static String idg = "0";
    private static int TICK_ID = 0;

    public static void handleLogin(String username, String password, FabricClientCommandSource source) {
        usernameg = username;
        passwordg = password;
        source.sendFeedback(Text.of("Данные сохранены"));
    }

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, commandRegistryAccess) -> {
            dispatcher.register(literal("banking")
                    .then(literal("login")
                            .then(argument("username", StringArgumentType.string())
                                    .then(argument("password", StringArgumentType.string())
                                            .executes(context -> {
                                                String username = StringArgumentType.getString(context, "username");
                                                String password = StringArgumentType.getString(context, "password");
                                                handleLogin(username, password, context.getSource());
                                                return 1;
                                            })
                                    )
                            )
                    )
                    .then(literal("get")
                            .executes(context -> {
                                FabricClientCommandSource source = context.getSource();
                                fetchAndDisplayBalance(source);
                                return 1;
                            })
                    )
                    .then(literal("pay")
                            .then(argument("account_reciver_id", StringArgumentType.string())
                                    .then(argument("transfer_amount", StringArgumentType.string())
                                            .then(argument("account_sender_id", StringArgumentType.string())
                                                .executes(context -> {
                                                    FabricClientCommandSource source = context.getSource();
                                                    String receiverId = StringArgumentType.getString(context, "account_reciver_id");
                                                    String senderId = StringArgumentType.getString(context, "account_sender_id");
                                                    String transferAmount = StringArgumentType.getString(context, "transfer_amount");
                                                    sendMoney(source, receiverId,transferAmount,senderId);
                                                    return 1;
                                                })
                                            )
                                    )
                            )
                    )
                    .then(literal("log")
                            .executes(context -> {
                                if (LOG) {
                                    LOG = false;
                                    context.getSource().sendFeedback(Text.of("Logging is disabled"));
                                } else {
                                    LOG = true;
                                    context.getSource().sendFeedback(Text.of("Logging is enabled"));
                                }
                                return 1;
                            })
                    )
            );
        });

        ClientTickEvents.END_WORLD_TICK.register(clientMinecraft -> {
            TICK_ID += 1;
            if (TICK_ID >= 40) {
                TICK_ID = 0;
                fetchAndAnnounceTransaction(clientMinecraft);
            }
        });
    }

    private static void fetchAndDisplayBalance(FabricClientCommandSource source) {
        HTTP_EXECUTOR.submit(() -> {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            String requestBody = "nickname=" + usernameg + "&password=" + passwordg;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://tochkaszap.temp.swtest.ru/api/external/get.php"))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .setHeader("Content-Type", "application/x-www-form-urlencoded")
                    .build();
            try {
                if (LOG) {
                    source.sendFeedback(Text.of(requestBody));
                }
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                String body = response.body();
                String amount = extractValue(body, AMOUNT_PATTERN);
                String nickname = extractValue(body, NICKNAME_PATTERN);
                String name = extractValue(body, NAME_PATTERN);
                String status = extractValue(body, STATUS_PATTERN);

                // Schedule the feedback to run on the main thread
// balance != null &&
                MinecraftClient.getInstance().execute(() -> {
                    if (nickname != null) {
//                        source.sendFeedback(Text.of("Баланс: " + balance + ", Никнейм: " + nickname));
                        source.sendFeedback(Text.of("Никнейм: " + nickname));
                        source.sendFeedback(Text.of(""));
                        source.sendFeedback(Text.of("Имя счёта: " + name));
                        source.sendFeedback(Text.of("Баланс: " + amount));
                        source.sendFeedback(Text.of("Статус: " + status));
                    } else {
                        source.sendFeedback(Text.of("Полный ответ: " + body));
                    }
                });
            } catch (IOException | InterruptedException e) {
                // Schedule the error message to run on the main thread
                MinecraftClient.getInstance().execute(() -> {
                    source.sendError(Text.of("Ошибка при получении баланса: " + e.getMessage()));
                });
                Thread.currentThread().interrupt(); // Restore interrupt status
            }
        });
    }

    private static void sendMoney(FabricClientCommandSource source, String receiverId, String transferAmount, String account_id) {
        HTTP_EXECUTOR.submit(() -> {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            String requestBody =
                    "nickname=" + usernameg +
                    "&password=" + passwordg +
                    "&account_receiver_id=" + receiverId +
                    "&transfer_amount=" + transferAmount +
                    "&account_sender_id="+account_id;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://tochkaszap.temp.swtest.ru/api/external/send_money.php"))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .setHeader("Content-Type", "application/x-www-form-urlencoded")
                    .build();
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                String body = response.body();
                String status = extractValue(body, STATUS_PATTERN);
                // Schedule the feedback to run on the main thread
                MinecraftClient.getInstance().execute(() -> {
                if (LOG) {
                    source.sendFeedback(Text.of(requestBody));
                }
                });
            } catch (IOException | InterruptedException e) {
                // Schedule the error message to run on the main thread
                MinecraftClient.getInstance().execute(() -> {
                    source.sendError(Text.of("Ошибка при отправке денег: " + e.getMessage()));
                });
                Thread.currentThread().interrupt(); // Restore interrupt status
            }
        });
    }

    private void fetchAndAnnounceTransaction(ClientWorld clientMinecraft) {
        HTTP_EXECUTOR.submit(() -> {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            String requestBody = "nickname=" + usernameg + "&password=" + passwordg;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://tochkaszap.temp.swtest.ru/api/external/get_last_transaction.php"))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .setHeader("Content-Type", "application/x-www-form-urlencoded")
                    .build();
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                String body = response.body();
                String id = extractValue(body, ID_PATTERN);
                if (!Objects.equals(id, idg) && id != null) {
                    String reciver_id = extractValue(body, RECIVER_ID_PATTERN);
                    String sender_id = extractValue(body, SENDER_ID_PATTERN);
                    String amount = extractValue(body, AMOUNT_PATTERN);
                    String date = extractValue(body, DATE_PATTERN);


                    idg = id;
                    // Schedule the broadcast to run on the main thread
                    MinecraftClient.getInstance().execute(() -> {
                        if (clientMinecraft != null) {

                            MinecraftClient.getInstance().getServer().getPlayerManager().broadcast(Text.of("Новый перевод!\nДата: " + date + "\nОтправитель: " + sender_id + "\nПолучатель: " + reciver_id + "\nСумма: " + amount), false);
                        }
                    });
                }
            } catch (IOException | InterruptedException e) {
                // Consider logging the error
                e.printStackTrace();
                Thread.currentThread().interrupt(); // Restore interrupt status
            }
        });
    }

    private static String extractValue(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}