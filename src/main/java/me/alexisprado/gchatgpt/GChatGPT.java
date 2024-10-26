package me.alexisprado.gchatgpt;

import gearth.extensions.Extension;
import gearth.extensions.ExtensionInfo;
import gearth.extensions.parsers.HEntity;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.json.JSONException;
import org.json.JSONObject;

@ExtensionInfo(
        Title = "GChatGPT",
        Description = "ChatGPT IA",
        Version = "1.1",
        Author = "AlexisPrado"
)

public class GChatGPT extends Extension {
    private String YourName;
    public int YourIndex = -1;
    private int chatPacketCount = 0;
    private int signPacketCount = 0;
    private long lastIncrementTime = 0;
    private String chatInstructions = "I will ask you for this. Answer a user question, but keep the response short and under 100 characters.";
    private String extraString = "Be smart. The output language is ''. The question is: ";
    private String language = "";
    private String chatMode = "none";
    private boolean gptenabled = false;

    private GChatGPT(String[] args) {
        super(args);
    }

    public static void main(String[] args) {
        new GChatGPT(args).run();
    }

    @Override
    protected void initExtension() {
        sendToServer(new HPacket("InfoRetrieve", HMessage.Direction.TOSERVER));
        intercept(HMessage.Direction.TOCLIENT, "Chat", this::InChat);
        intercept(HMessage.Direction.TOCLIENT, "Shout", this::InChat);
        intercept(HMessage.Direction.TOCLIENT, "UserObject", this::InUserObject);
        intercept(HMessage.Direction.TOCLIENT, "Users", this::InUsers);
        intercept(HMessage.Direction.TOSERVER, "Chat", this::OnChat);
    }

    private void InUsers(HMessage hMessage) {
        try {
            HPacket hPacket = hMessage.getPacket();
            HEntity[] roomUsersList = HEntity.parse(hPacket);
            for (HEntity hEntity : roomUsersList) {
                if (YourName.equals(hEntity.getName())) {
                    YourIndex = hEntity.getIndex();
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void OnChat(HMessage hMessage) {
        String message = hMessage.getPacket().readString();

        if (message.startsWith(":gpt lang ")) {
            language = message.substring(":gpt lang ".length());
            hMessage.setBlocked(true);
            sendToClient(new HPacket("Whisper", HMessage.Direction.TOCLIENT, -1, "GPT: Language set to '" + language + "'.", 0, 30, 0, -1));
        }

        if (message.equals(":gpt mode sarcasm") || message.equals(":gpt s")) {
            hMessage.setBlocked(true);
            chatMode = "sarcasm";
            sendToClient(new HPacket("Whisper", HMessage.Direction.TOCLIENT, -1, "GPT: Sarcasm mode activated.", 0, 30, 0, -1));
        } else if (message.equals(":gpt mode earnest") || message.equals(":gpt e")) {
            hMessage.setBlocked(true);
            chatMode = "earnest";
            sendToClient(new HPacket("Whisper", HMessage.Direction.TOCLIENT, -1, "GPT: Earnest mode activated.", 0, 30, 0, -1));
        }

        if (message.equals(":gpt on")) {
            hMessage.setBlocked(true);
            gptenabled = true;
            sendToClient(new HPacket("Whisper", HMessage.Direction.TOCLIENT, -1, "GPT: Enabled.", 0, 30, 0, -1));
        } else if (message.equals(":gpt off")) {
            hMessage.setBlocked(true);
            gptenabled = false;
            sendToClient(new HPacket("Whisper", HMessage.Direction.TOCLIENT, -1, "GPT: Disabled.", 0, 30, 0, -1));
        }

        if (chatMode.equals("sarcasm")) {
            chatInstructions = "I will ask you for this. Answer a user's question, but keep the response short and under 100 characters. Use modern internet language. No hashtags, emoticons, or emojis.";
            extraString = "Be Friendly, smart and give cool humor answers with fresh answers and coolness and a little bit smart-ass with modern internet language. The Output Language is '" + language + "'. The question is: ";
        } else if (chatMode.equals("earnest")) {
            chatInstructions = "I will ask you for this. Answer a user question, but keep the response short and under 100 characters.";
            extraString = "Be smart. The output language is '" + language + "'. The question is: ";
        }
    }

    private void InUserObject(HMessage hMessage) {
        hMessage.getPacket().readInteger();
        YourName = hMessage.getPacket().readString();
    }

    private void InChat(HMessage hMessage) {
        if (gptenabled) {
            int index = hMessage.getPacket().readInteger();
            String prompt = hMessage.getPacket().readString();

            if (index != YourIndex) {
                String[] prefixes = {":gpt ", "@red@:gpt ", "@green@:gpt ", "@purple@:gpt ", "@blue@:gpt ", "@cyan@:gpt ", ":ChatGPT ", ": " + YourName + " "};
                for (String prefix : prefixes) {
                    if (prompt.startsWith(prefix)) {
                        String chatbotPrompt = prompt.substring(prefix.length());
                        String chatbotResponse = getChatbotResponse(chatInstructions + " " + extraString + chatbotPrompt);
                        System.out.println(chatInstructions + " " + extraString + chatbotPrompt);
                        if (chatPacketCount < 4) {
                            if (chatbotResponse.length() > 100) {
                                chatbotResponse = "I can't write the complete answer because it was too long as it exceeds 100 characters.";
                            }
                            long currentMillis = System.currentTimeMillis();
                            if (currentMillis - lastIncrementTime > 4000) {
                                chatPacketCount = 0;
                            }

                            lastIncrementTime = currentMillis;
                            sendToServer(new HPacket("Chat", HMessage.Direction.TOSERVER, chatbotResponse, 0, 0));
                            chatPacketCount++;
                            System.out.println(chatPacketCount);
                            System.out.println(chatbotResponse);
                        } else {
                            sendToServer(new HPacket("Sign", HMessage.Direction.TOSERVER, 13));
                            signPacketCount++;
                        }
                        if (signPacketCount == 1) {
                            startResetThread();
                        }
                    }
                }
            }
        }
    }

    private void startResetThread() {
        new Thread(() -> {
            try {
                Thread.sleep(6000);
                chatPacketCount = 0;
                signPacketCount = 0;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private String getChatbotResponse(String userMessage) {
        String apiUrl;
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        StringBuilder response = new StringBuilder();

        try {
            String encodedMessage = URLEncoder.encode(userMessage, StandardCharsets.UTF_8.toString());
            apiUrl = "https://free-unoficial-gpt4o-mini-api-g70n.onrender.com/chat/?query=" + encodedMessage;

            URL url = new URL(apiUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");

            reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }

            JSONObject jsonResponse = new JSONObject(response.toString());
            return jsonResponse.getString("results");

        } catch (IOException | JSONException e) {
            e.printStackTrace();
            return "Error: Unable to get a response from the chatbot.";
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}