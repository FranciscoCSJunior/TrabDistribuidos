package StableMulticast;

import java.util.HashMap;
import java.util.Map;

class VectorClock
{
    public Map<String, Integer> vc;

    public VectorClock()
    {
        vc = new HashMap<>();
    }

    public void add(String ip)
    {
        if (!vc.containsKey(ip)) {
            vc.put(ip, 0); // Inicializa com 0 em vez de -1
        }
    }

    public void add(String ip, Integer value)
    {
        vc.put(ip, value);
    }

    public Integer get(String ip)
    {
        Integer value = vc.get(ip);
        return value != null ? value : 0; // Retorna 0 se não existe
    }

    public void copy(VectorClock original)
    {
        vc.clear();
        vc.putAll(original.vc);
    }

    public void incrementOne(String ip)
    {
        Integer currentValue = get(ip); // Usa get() que retorna 0 se não existe
        vc.put(ip, currentValue + 1);
    }

    public String serialize()
    {
        if (vc.isEmpty()) {
            return "";
        }

        StringBuilder serialized = new StringBuilder();
        for (Map.Entry<String, Integer> entry : vc.entrySet())
        {
            serialized.append(entry.getKey()).append(":").append(entry.getValue()).append(";");
        }

        // Remove o último ';'
        if (serialized.length() > 0) {
            serialized.setLength(serialized.length() - 1);
        }

        return serialized.toString();
    }

    @Override
    public String toString()
    {
        if (vc.isEmpty()) {
            return "{}";
        }

        StringBuilder result = new StringBuilder();
        result.append("{");

        boolean first = true;
        for (Map.Entry<String, Integer> entry : vc.entrySet())
        {
            if (!first) {
                result.append(", ");
            }
            result.append(entry.getKey()).append(":").append(entry.getValue());
            first = false;
        }

        result.append("}");
        return result.toString();
    }
}