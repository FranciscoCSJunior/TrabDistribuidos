package StableMulticast;

import java.util.HashMap;
import java.util.Map;

class MatrixClock
{
    public Map<String, VectorClock> mc;

    public MatrixClock()
    {
        mc = new HashMap<>();
    }

    public Integer getMinTimeOfColumn(String column)
    {
        Integer minValue = Integer.MAX_VALUE;
        boolean encontrouValor = false;

        for (VectorClock vc : mc.values())
        {
            Integer valor = vc.get(column);
            if (valor != null) {
                if (valor < minValue) {
                    minValue = valor;
                }
                encontrouValor = true;
            }
        }

        // Se não encontrou nenhum valor, retorna 0
        return encontrouValor ? minValue : 0;
    }

    public void addVectorClock(String ip, VectorClock newVectorClock)
    {
        VectorClock copy = new VectorClock();
        copy.copy(newVectorClock);
        mc.put(ip, copy);
    }

    public void addNewParticipant(String ip)
    {
        // Se já existe, não faz nada
        if (mc.containsKey(ip)) {
            return;
        }

        // Adiciona o novo participante a todos os relógios existentes
        for (VectorClock vc : mc.values())
        {
            vc.add(ip, 0); // Inicializa com 0
        }

        // Cria novo relógio vetorial para o novo participante
        VectorClock newVectorClock = new VectorClock();

        // Adiciona todos os participantes existentes no novo relógio
        for (String existingIp : mc.keySet())
        {
            newVectorClock.add(existingIp, 0);
        }

        // Adiciona ele mesmo
        newVectorClock.add(ip, 0);

        // Adiciona à matriz
        addVectorClock(ip, newVectorClock);

        //System.out.println("DEBUG: Participante " + ip + " adicionado à matriz de relógios");
        //System.out.println("DEBUG: Relógio do novo participante: " + newVectorClock);
    }

    @Override
    public String toString()
    {
        if (mc.isEmpty()) {
            return "Matriz de Relógios: (vazia)";
        }

        StringBuilder obj = new StringBuilder();
        obj.append("Matriz de Relógios:\n");

        for (Map.Entry<String, VectorClock> entry : mc.entrySet())
        {
            String ip = entry.getKey();
            VectorClock vc = entry.getValue();
            obj.append("  ").append(ip).append(": ").append(vc).append("\n");
        }

        return obj.toString();
    }
}