package tinyllm;

import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;
import javax.microedition.io.*;
import java.io.*;

public class LLMMidlet extends MIDlet implements CommandListener {
    private Display display;
    private Form mainForm;
    private Form responseForm;
    private Form settingsForm;
    private TextField messageField;
    private TextField serverField;
    private TextField keyField;
    private ChoiceGroup modelChoice;
    private StringItem responseItem;
    private Command sendCommand;
    private Command exitCommand;
    private Command backCommand;
    private Command settingsCommand;
    private Command saveSettingsCommand;

    // Default configuration - update these for your setup
    private String proxyUrl = "http://litellm.issei.space:4000";
    private String proxyKey = "sk-xxxxx";
    private String currentModel = "openrouter/qwen/qwen3-30b-a3b";

    public void startApp() {
        display = Display.getDisplay(this);
        loadSettings(); // Load saved settings if available
        initUI();
        display.setCurrent(mainForm);
    }

    public void pauseApp() {}

    public void destroyApp(boolean unconditional) {
        saveSettings(); // Save settings before exit
        notifyDestroyed();
    }

    private void initUI() {
        // Main chat form
        mainForm = new Form("TinyLLM");
        messageField = new TextField("Your message:", "", 500, TextField.ANY);
        sendCommand = new Command("Send", Command.OK, 1);
        settingsCommand = new Command("Settings", Command.SCREEN, 2);
        exitCommand = new Command("Exit", Command.EXIT, 3);

        mainForm.append(messageField);
        mainForm.addCommand(sendCommand);
        mainForm.addCommand(settingsCommand);
        mainForm.addCommand(exitCommand);
        mainForm.setCommandListener(this);

        // Response form
        responseForm = new Form("LLMResponse");
        responseItem = new StringItem("", "");
        backCommand = new Command("Back", Command.BACK, 1);

        responseForm.append(responseItem);
        responseForm.addCommand(backCommand);
        responseForm.addCommand(exitCommand);
        responseForm.setCommandListener(this);

        // Settings form
        settingsForm = new Form("Settings");
        serverField = new TextField("Server URL:", proxyUrl, 200, TextField.URL);
        keyField = new TextField("API Key:", proxyKey, 100, TextField.PASSWORD);

        modelChoice = new ChoiceGroup("Model:", Choice.EXCLUSIVE);
        modelChoice.append("qwen3-30b-a3b", null);
        modelChoice.append("gpt-4.1-mini", null);
        modelChoice.setSelectedIndex(0, true);

        saveSettingsCommand = new Command("Save", Command.OK, 1);

        settingsForm.append(serverField);
        settingsForm.append(keyField);
        settingsForm.append(modelChoice);
        settingsForm.addCommand(saveSettingsCommand);
        settingsForm.addCommand(backCommand);
        settingsForm.setCommandListener(this);
    }

    public void commandAction(Command c, Displayable d) {
        if (c == exitCommand) {
            destroyApp(false);
        } else if (c == sendCommand) {
            String message = messageField.getString().trim();
            if (message.length() > 0) {
                sendMessage(message);
            }
        } else if (c == backCommand) {
            if (d == responseForm) {
                messageField.setString(""); // Clear message
                display.setCurrent(mainForm);
            } else if (d == settingsForm) {
                display.setCurrent(mainForm);
            }
        } else if (c == settingsCommand) {
            // Update settings form with current values
            serverField.setString(proxyUrl);
            keyField.setString(proxyKey);
            display.setCurrent(settingsForm);
        } else if (c == saveSettingsCommand) {
            // Save settings
            proxyUrl = serverField.getString().trim();
            proxyKey = keyField.getString().trim();

            int selectedModel = modelChoice.getSelectedIndex();
            currentModel = selectedModel == 0 ? "openrouter/qwen/qwen3-30b-a3b" : "openrouter/openai/gpt-4.1-mini";

            saveSettings();
            display.setCurrent(mainForm);
        }
    }

    private void sendMessage(String message) {
        responseItem.setText("Connecting to " + currentModel + "...");
        display.setCurrent(responseForm);

        Thread networkThread = new Thread(new LiteLLMTask(message));
        networkThread.start();
    }

    class LiteLLMTask implements Runnable {
        private String message;

        public LiteLLMTask(String message) {
            this.message = message;
        }

        public void run() {
            try {
                String endpoint = proxyUrl + "/chat/completions";
                HttpConnection connection = (HttpConnection) Connector.open(endpoint);

                // Set headers for LiteLLM proxy
                connection.setRequestMethod(HttpConnection.POST);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + proxyKey);
                connection.setRequestProperty("User-Agent", "J2ME-Client/1.0");

                // Create OpenAI-compatible request body
                String requestBody = createRequestBody(message);
                connection.setRequestProperty("Content-Length", String.valueOf(requestBody.length()));

                updateResponse("Sending request...");

                // Send request
                OutputStream os = connection.openOutputStream();
                os.write(requestBody.getBytes("UTF-8"));
                os.close();

                // Read response
                int responseCode = connection.getResponseCode();
                InputStream is;

                if (responseCode == HttpConnection.HTTP_OK) {
                    is = connection.openInputStream();
                } else {
                    is = connection.openInputStream(); // Try to read error response
                }

                String response = readResponse(is);
                is.close();
                connection.close();

                if (responseCode == HttpConnection.HTTP_OK) {
                    String extractedText = extractResponseText(response);
                    updateResponse(extractedText);
                } else {
                    updateResponse("Error " + responseCode + ": " + response);
                }

            } catch (Exception e) {
                updateResponse("Network error: " + e.getMessage());
            }
        }
    }

    private String createRequestBody(String message) {
        // Create OpenAI-compatible JSON request
        // Note: This is a simple JSON builder for J2ME limitations
        StringBuffer json = new StringBuffer();
        json.append("{");
        json.append("\"model\":\"").append(currentModel).append("\",");
        json.append("\"messages\":[{");
        json.append("\"role\":\"user\",");
        json.append("\"content\":\"").append(escapeJson(message)).append("\"");
        json.append("}],");
        json.append("\"max_tokens\":150,");
        json.append("\"temperature\":0.7");
        json.append("}");

        return json.toString();
    }

    private String escapeJson(String text) {
        // Basic JSON escaping for J2ME
        StringBuffer escaped = new StringBuffer();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"':
                    escaped.append("\\\"");
                    break;
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                default:
                    escaped.append(c);
            }
        }
        return escaped.toString();
    }

    private String extractResponseText(String jsonResponse) {
        // Simple JSON parsing for J2ME (looking for content field)
        try {
            // Look for "content":"text"
            String contentMarker = "\"content\":\"";
            int contentStart = jsonResponse.indexOf(contentMarker);
            if (contentStart == -1) {
                return "Could not parse response";
            }

            contentStart += contentMarker.length();
            int contentEnd = jsonResponse.indexOf("\"", contentStart);

            // Handle escaped quotes
            while (contentEnd > 0 && jsonResponse.charAt(contentEnd - 1) == '\\') {
                contentEnd = jsonResponse.indexOf("\"", contentEnd + 1);
            }

            if (contentEnd == -1) {
                return "Incomplete response";
            }

            String content = jsonResponse.substring(contentStart, contentEnd);
            return unescapeJson(content);

        } catch (Exception e) {
            return "Parse error: " + e.getMessage();
        }
    }

    private String unescapeJson(String escaped) {
        // Basic JSON unescaping
        StringBuffer unescaped = new StringBuffer();
        for (int i = 0; i < escaped.length(); i++) {
            char c = escaped.charAt(i);
            if (c == '\\' && i + 1 < escaped.length()) {
                char next = escaped.charAt(i + 1);
                switch (next) {
                    case '"':
                        unescaped.append('"');
                        i++;
                        break;
                    case '\\':
                        unescaped.append('\\');
                        i++;
                        break;
                    case 'n':
                        unescaped.append('\n');
                        i++;
                        break;
                    case 'r':
                        unescaped.append('\r');
                        i++;
                        break;
                    case 't':
                        unescaped.append('\t');
                        i++;
                        break;
                    default:
                        unescaped.append(c);
                }
            } else {
                unescaped.append(c);
            }
        }
        return unescaped.toString();
    }

    private String readResponse(InputStream is) throws IOException {
        StringBuffer buffer = new StringBuffer();
        int ch;
        while ((ch = is.read()) != -1) {
            buffer.append((char) ch);
        }
        return buffer.toString();
    }

    private void updateResponse(final String response) {
        display.callSerially(new Runnable() {
            public void run() {
                String displayText = splitText(response, 50);
                responseItem.setText(displayText);
            }
        });
    }

    private String splitText(String text, int maxLineLength) {
        if (text.length() <= maxLineLength) {
            return text;
        }

        StringBuffer result = new StringBuffer();
        int start = 0;

        while (start < text.length()) {
            int end = Math.min(start + maxLineLength, text.length());

            if (end < text.length()) {
                int lastSpace = text.lastIndexOf(' ', end);
                if (lastSpace > start) {
                    end = lastSpace;
                }
            }

            result.append(text.substring(start, end));
            if (end < text.length()) {
                result.append("\n");
            }

            start = end + (end < text.length() && text.charAt(end) == ' ' ? 1 : 0);
        }

        return result.toString();
    }

    // Simple settings persistence using RMS (Record Management System)
    private void loadSettings() {
        // TODO
    }

    private void saveSettings() {
        // TODO
    }
}
