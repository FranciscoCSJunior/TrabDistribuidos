package StableMulticast;

class Message
{
    public String message;
    public String sender;
    public VectorClock vc;

    public Message(String fullMessage)
    {
        //System.out.println("DEBUG: Deserializando mensagem: " + fullMessage);

        String[] parts = fullMessage.split(";");

        if (parts.length < 3) {
            throw new IllegalArgumentException("Formato de mensagem inválido: " + fullMessage);
        }

        message = parts[0];
        sender = parts[1];

        //System.out.println("DEBUG: Mensagem extraída: " + message);
        //System.out.println("DEBUG: Remetente extraído: " + sender);

        vc = new VectorClock();

        // Processa as partes do relógio vetorial
        for (int i = 2; i < parts.length; i++)
        {
            if (parts[i].contains(":")) {
                String[] key_value = parts[i].split(":");
                if (key_value.length == 2) {
                    try {
                        String ip = key_value[0];
                        Integer valor = Integer.parseInt(key_value[1]);
                        vc.add(ip, valor);
                        //System.out.println("DEBUG: Adicionado ao VC: " + ip + " = " + valor);
                    } catch (NumberFormatException e) {
                        System.err.println("Erro ao converter valor do relógio: " + key_value[1]);
                    }
                }
            }
        }

        //System.out.println("DEBUG: Relógio vetorial deserializado: " + vc);
    }

    public Integer getTime()
    {
        Integer time = vc.get(sender);
        //System.out.println("DEBUG: Tempo da mensagem de " + sender + ": " + time);
        return time;
    }

    @Override
    public String toString()
    {
        StringBuilder obj = new StringBuilder();
        obj.append("Message: ").append(message).append("\n");
        obj.append("Sender: ").append(sender).append("\n");
        obj.append("VC: ").append(vc).append("\n");
        return obj.toString();
    }

    public static String serialize(String message, String ip, VectorClock vc)
    {
        StringBuilder fullMessage = new StringBuilder();
        fullMessage.append(message);
        fullMessage.append(";").append(ip);
        fullMessage.append(";").append(vc.serialize());

        String result = fullMessage.toString();
        //System.out.println("DEBUG: Mensagem serializada: " + result);

        return result;
    }
}