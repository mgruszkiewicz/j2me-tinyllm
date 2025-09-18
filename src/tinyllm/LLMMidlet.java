package tinyllm;

import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;
import javax.microedition.io.*;
import javax.microedition.rms.*;
import java.io.*;
import java.util.Vector;

public class LLMMidlet extends MIDlet implements CommandListener {
    private Display display;
    private Form mainForm;
    private Form chatForm;
    private Form settingsForm;
    private List conversationsList;
    private TextField messageField;
    private TextField serverField;
    private TextField keyField;
    private ChoiceGroup modelChoice;
    private Command sendCommand;
    private Command exitCommand;
    private Command backCommand;
    private Command settingsCommand;
    private Command saveSettingsCommand;

    private Vector messages;
    private Vector conversations; // Vector of Conversation
    private boolean isLoadedChat;
    private Conversation currentConv;
    private int userMessageCount;

    class Message {
        String role;
        String content;
        Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    class Conversation {
        long timestamp;
        String title;
        Vector messages;
        Conversation(long timestamp, Vector messages) {
            this.timestamp = timestamp;
            this.title = "Chat " + timestamp;
            this.messages = messages;
        }
        String getTitle() {
            return title;
        }
    }

    // Default configuration - update these for your setup
    private String proxyUrl = "http://litellm.issei.space:4000";
    private String proxyKey = "sk-eHCygV4zQ6KbbwTVEPwafw";
    private String currentModel = "openrouter/qwen/qwen3-30b-a3b";
    
    public void startApp() {
        display = Display.getDisplay(this);
        loadSettings(); // Load saved settings if available
        loadConversations();
        initUI();
        display.setCurrent(conversationsList);
    }
    
    public void pauseApp() {}
    
    public void destroyApp(boolean unconditional) {
        saveSettings(); // Save settings before exit
        saveConversations();
        notifyDestroyed();
    }
    
    private void initUI() {
        // Conversations list
        conversationsList = new List("Conversations", List.IMPLICIT);
        settingsCommand = new Command("Settings", Command.SCREEN, 1);
        exitCommand = new Command("Exit", Command.EXIT, 2);

        conversationsList.addCommand(settingsCommand);
        conversationsList.addCommand(exitCommand);
        conversationsList.setCommandListener(this);

        // Populate conversations list
        conversationsList.append("Create New Conversation", null);
        for (int i = 0; i < conversations.size(); i++) {
            Conversation conv = (Conversation) conversations.elementAt(i);
            conversationsList.append(conv.getTitle(), null);
        }

        // Chat form
        chatForm = new Form("Chat");
        messageField = new TextField("Your message:", "", 500, TextField.ANY);
        sendCommand = new Command("Send", Command.OK, 1);
        backCommand = new Command("Back", Command.BACK, 2);

        chatForm.append(messageField);
        chatForm.addCommand(sendCommand);
        chatForm.addCommand(backCommand);
        chatForm.addCommand(exitCommand);
        chatForm.setCommandListener(this);
        
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
            saveConversations();
            destroyApp(false);
        } else if (c == sendCommand && d == chatForm) {
            String message = messageField.getString().trim();
            if (message.length() > 0) {
                appendMessage(new Message("user", message));
                messageField.setString("");
                sendMessage(message);
            }
        } else if (c == backCommand) {
            if (d == chatForm) {
                saveCurrentConversation();
                display.setCurrent(conversationsList);
            } else if (d == settingsForm) {
                display.setCurrent(conversationsList);
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
            display.setCurrent(conversationsList);
        } else if (c == List.SELECT_COMMAND && d == conversationsList) {
            int index = conversationsList.getSelectedIndex();
            if (index == 0) {
                startNewChat();
            } else if (index > 0 && index <= conversations.size()) {
                loadChat((Conversation) conversations.elementAt(index - 1));
            }
        }
    }
    
    private void sendMessage(String message) {
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
                    appendMessage(new Message("assistant", extractedText));
                } else {
                    appendMessage(new Message("assistant", "Error " + responseCode + ": " + response));
                }
                
            } catch (Exception e) {
                appendMessage(new Message("assistant", "Network error: " + e.getMessage()));
            }
        }
    }
    
    private String createRequestBody(String message) {
        // Create OpenAI-compatible JSON request with conversation history
        StringBuffer json = new StringBuffer();
        json.append("{");
        json.append("\"model\":\"").append(currentModel).append("\",");
        json.append("\"messages\":[");
        for (int i = 0; i < messages.size(); i++) {
            Message msg = (Message) messages.elementAt(i);
            if (i > 0) json.append(",");
            json.append("{");
            json.append("\"role\":\"").append(msg.role).append("\",");
            json.append("\"content\":\"").append(escapeJson(msg.content)).append("\"");
            json.append("}");
        }
        json.append("],");
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
        try {
            RecordStore rs = RecordStore.openRecordStore("TinyLLMSettings", true);
            if (rs.getNumRecords() > 0) {
                byte[] data = rs.getRecord(1);
                String settings = new String(data, "UTF-8");
                String[] parts = split(settings, '|');
                if (parts.length >= 3) {
                    proxyUrl = parts[0];
                    proxyKey = parts[1];
                    currentModel = parts[2];
                }
            }
            rs.closeRecordStore();
        } catch (Exception e) {
            // Use defaults if loading fails
        }
    }

    private void saveSettings() {
        try {
            RecordStore rs = RecordStore.openRecordStore("TinyLLMSettings", true);
            String settings = proxyUrl + "|" + proxyKey + "|" + currentModel;
            byte[] data = settings.getBytes("UTF-8");
            if (rs.getNumRecords() > 0) {
                rs.setRecord(1, data, 0, data.length);
            } else {
                rs.addRecord(data, 0, data.length);
            }
            rs.closeRecordStore();
        } catch (Exception e) {
            // Ignore save errors
        }
    }

    private void loadConversations() {
        conversations = new Vector();
        try {
            RecordStore rs = RecordStore.openRecordStore("TinyLLMConversations", true);
            RecordEnumeration re = rs.enumerateRecords(null, null, false);
            while (re.hasNextElement()) {
                int id = re.nextRecordId();
                byte[] data = rs.getRecord(id);
                String convStr = new String(data, "UTF-8");
                Conversation conv = parseConversation(convStr);
                if (conv != null) {
                    conversations.addElement(conv);
                }
            }
            rs.closeRecordStore();
        } catch (Exception e) {
            // Use empty if loading fails
        }
    }

    private void saveConversations() {
        try {
            RecordStore.deleteRecordStore("TinyLLMConversations");
            RecordStore rs = RecordStore.openRecordStore("TinyLLMConversations", true);
            for (int i = 0; i < conversations.size(); i++) {
                Conversation conv = (Conversation) conversations.elementAt(i);
                String convStr = serializeConversation(conv);
                byte[] data = convStr.getBytes("UTF-8");
                rs.addRecord(data, 0, data.length);
            }
            rs.closeRecordStore();
        } catch (Exception e) {
            // Ignore save errors
        }
    }

    private void startNewChat() {
        messages = new Vector();
        isLoadedChat = false;
        long ts = System.currentTimeMillis();
        currentConv = new Conversation(ts, messages);
        userMessageCount = 0;
        updateChatDisplay();
        display.setCurrent(chatForm);
    }

    private void loadChat(Conversation conv) {
        messages = conv.messages;
        isLoadedChat = true;
        currentConv = conv;
        userMessageCount = 0;
        for (int i = 0; i < messages.size(); i++) {
            if (((Message) messages.elementAt(i)).role.equals("user")) {
                userMessageCount++;
            }
        }
        updateChatDisplay();
        display.setCurrent(chatForm);
    }

    private void saveCurrentConversation() {
        if (messages != null && messages.size() > 0) {
            if (!isLoadedChat) {
                conversations.addElement(currentConv);
                conversationsList.append(currentConv.getTitle(), null);
            }
            // For loaded chats, messages are already updated in the Conversation object
        }
    }

    private void appendMessage(Message msg) {
        messages.addElement(msg);
        updateChatDisplay();
        // Set title to first user message (first 20 chars)
        if (msg.role.equals("user")) {
            userMessageCount++;
            if (userMessageCount == 1 && !isLoadedChat) {
                currentConv.title = msg.content.length() > 20 ? msg.content.substring(0, 20) : msg.content;
            }
        }
    }

    private void updateChatDisplay() {
        chatForm.deleteAll();
        for (int i = 0; i < messages.size(); i++) {
            Message msg = (Message) messages.elementAt(i);
            String prefix = msg.role.equals("user") ? "You: " : "AI: ";
            StringItem item = new StringItem(null, prefix + msg.content);
            chatForm.append(item);
        }
        chatForm.append(messageField);
    }



    private Conversation parseConversation(String convStr) {
        String[] parts = split(convStr, '|');
        if (parts.length < 2) return null;
        long timestamp = Long.parseLong(parts[0]);
        String title;
        Vector msgs = new Vector();
        int startMsgs;
        if (parts.length > 2 && !parts[1].startsWith("user:") && !parts[1].startsWith("assistant:")) {
            title = parts[1];
            startMsgs = 2;
        } else {
            title = "Chat " + timestamp;
            startMsgs = 1;
        }
        for (int i = startMsgs; i < parts.length; i++) {
            String[] msgParts = split(parts[i], ':');
            if (msgParts.length == 2) {
                msgs.addElement(new Message(msgParts[0], msgParts[1]));
            }
        }
        Conversation conv = new Conversation(timestamp, msgs);
        conv.title = title;
        return conv;
    }

    private String serializeConversation(Conversation conv) {
        StringBuffer sb = new StringBuffer();
        sb.append(conv.timestamp);
        sb.append('|').append(conv.title);
        for (int i = 0; i < conv.messages.size(); i++) {
            Message msg = (Message) conv.messages.elementAt(i);
            sb.append('|').append(msg.role).append(':').append(msg.content);
        }
        return sb.toString();
    }

    private String[] split(String str, char delimiter) {
        java.util.Vector v = new java.util.Vector();
        int start = 0;
        int end = str.indexOf(delimiter);
        while (end != -1) {
            v.addElement(str.substring(start, end));
            start = end + 1;
            end = str.indexOf(delimiter, start);
        }
        v.addElement(str.substring(start));
        String[] result = new String[v.size()];
        v.copyInto(result);
        return result;
    }
}