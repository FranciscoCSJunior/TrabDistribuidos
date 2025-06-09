package StableMulticast;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.io.*;
import java.net.*;
import java.util.Scanner;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class StableMulticast
{
    private String ip;
    private Integer porta;
    private IStableMulticast cliente;

    private List<Message> bufferMensagens;
    private MatrixClock mc;
    private MulticastSocket socketMulticast;
    private DatagramSocket socketUnicast;
    private InetAddress grupoMulticast;
    private List<String> participantes;
    private AtomicBoolean sistemaInicializado;

    public StableMulticast(String ip, Integer porta, IStableMulticast cliente) {
        this.ip = ip;
        this.porta = porta;
        this.cliente = cliente;
        this.bufferMensagens = new ArrayList<>();
        this.participantes = new CopyOnWriteArrayList<>();
        this.sistemaInicializado = new AtomicBoolean(false);

        // Adiciona este processo aos participantes
        String meuEnderecoCompleto = ip + ":" + porta;
        this.participantes.add(meuEnderecoCompleto);

        // Inicializa o relógio matricial
        VectorClock relogioVetorialInicial = new VectorClock();
        relogioVetorialInicial.add(ip, 0);
        mc = new MatrixClock();
        mc.addVectorClock(ip, relogioVetorialInicial);

        try {
            // Configura socket multicast
            this.grupoMulticast = InetAddress.getByName("230.0.0.1");
            this.socketMulticast = new MulticastSocket(54321);
            this.socketMulticast.setSoTimeout(2000); // Timeout menor para descoberta mais rápida
            this.socketMulticast.joinGroup(grupoMulticast);

            // Configura socket unicast com porta alternativa se necessário
            int tentativas = 0;
            boolean socketCriado = false;
            int portaOriginal = porta;

            while (tentativas < 10 && !socketCriado) {
                try {
                    this.socketUnicast = new DatagramSocket(portaOriginal + tentativas);
                    this.porta = portaOriginal + tentativas; // Atualiza porta se alterada
                    socketCriado = true;
                    System.out.println("Socket unicast criado na porta: " + this.porta);
                } catch (BindException e) {
                    tentativas++;
                    if (tentativas == 10) {
                        throw new RuntimeException("Não foi possível encontrar uma porta disponível");
                    }
                }
            }

            // Atualiza o endereço nos participantes se a porta mudou
            if (this.porta != portaOriginal) {
                participantes.clear();
                participantes.add(ip + ":" + this.porta);
            }

            // Inicia as threads de escuta
            new Thread(this::escutarMulticast).start();
            new Thread(() -> {
                try {
                    escutarUnicast();
                } catch (Exception e) {
                    System.err.println("Erro no listener unicast: " + e.getMessage());
                    e.printStackTrace();
                }
            }).start();

            // Aguarda um pouco antes de iniciar descoberta
            Thread.sleep(1000);

            // Inicia processo de descoberta contínua
            new Thread(this::processoDescoberta).start();

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Erro ao inicializar sockets: " + e.getMessage(), e);
        }
    }

    private void processoDescoberta() {
        System.out.println("Iniciando processo de descoberta...");

        // Envia anúncios periódicos nos primeiros 15 segundos
        for (int i = 0; i < 15; i++) {
            enviarAnuncioMulticast();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        // Marca sistema como inicializado
        sistemaInicializado.set(true);
        System.out.println("Sistema inicializado. Participantes descobertos: " + participantes.size());
        imprimirEstadoSistema();

        // Continua enviando anúncios menos frequentemente
        while (true) {
            try {
                Thread.sleep(10000); // A cada 10 segundos
                enviarAnuncioMulticast();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public void msend(String msg, IStableMulticast clienteParam) {
        // Verifica se o sistema está inicializado
        if (!sistemaInicializado.get()) {
            System.out.println("Sistema ainda não está totalmente inicializado. Aguarde...");
            return;
        }

        if (participantes == null || participantes.size() <= 1) {
            System.out.println("Nenhum outro participante disponível para envio de mensagens.");
            System.out.println("Participantes atuais: " + participantes);
            return;
        }

        // Implementação do algoritmo: msg.VC = MC_i[i][*] (constrói timestamp)
        VectorClock relogioAtual = mc.mc.get(ip);
        if (relogioAtual == null) {
            System.err.println("Erro: Relógio vetorial não encontrado para " + ip);
            return;
        }

        // Incrementa primeiro: MC_i[i][i] = MC_i[i][i] + 1 (Pi fez mais um multicast)
        relogioAtual.incrementOne(ip);

        // Cria mensagem com timestamp atual
        String mensagemCompleta = Message.serialize(msg, ip, relogioAtual);

        System.out.println("\n=== Controle de Envio ===");
        System.out.println("Mensagem: \"" + msg + "\"");
        System.out.println("Timestamp: " + relogioAtual);
        System.out.println("Destinatários disponíveis:");
        for (String participante : participantes) {
            if (!participante.startsWith(ip + ":")) {
                System.out.println("  - " + participante);
            }
        }
        System.out.println("Enviar para TODOS? (s/n)");

        Scanner scanner = new Scanner(System.in);
        String resposta = scanner.nextLine();
        List<String> destinatarios = new ArrayList<>();

        if (resposta.equalsIgnoreCase("s") || resposta.equalsIgnoreCase("y")) {
            for (String participante : participantes) {
                if (!participante.startsWith(ip + ":")) {
                    destinatarios.add(participante);
                }
            }
        } else {
            for (String participante : participantes) {
                if (!participante.startsWith(ip + ":")) {
                    System.out.println("Enviar para " + participante + "? (s/n)");
                    resposta = scanner.nextLine();
                    if (resposta.equalsIgnoreCase("s") || resposta.equalsIgnoreCase("y")) {
                        destinatarios.add(participante);
                    }
                }
            }
        }

        // Envia para todos os destinatários selecionados
        int enviosRealizados = 0;
        for (String destinatario : destinatarios) {
            if (enviarMensagemUnicast(mensagemCompleta, destinatario)) {
                enviosRealizados++;
            }
        }

        System.out.println("Envios realizados: " + enviosRealizados + "/" + destinatarios.size());
        System.out.println("Relógio local após envio: " + relogioAtual);

        imprimirEstadoSistema();
    }

    private void receberMensagem(String msgStr) {
        try {
            Message msg = new Message(msgStr);

            System.out.println("\n=== Mensagem Recebida ===");
            System.out.println("De: " + msg.sender);
            System.out.println("Conteúdo: " + msg.message);
            System.out.println("Relógio Vetorial da Mensagem: " + msg.vc);

            // Adiciona mensagem ao buffer
            bufferMensagens.add(msg);

            // Garante que o remetente existe na matriz de relógios
            if (!mc.mc.containsKey(msg.sender)) {
                System.out.println("Adicionando novo participante ao relógio matricial: " + msg.sender);
                mc.addNewParticipant(msg.sender);
            }

            // Implementação correta do algoritmo de estabilização
            // MC_i[j][*] = msg.VC (atualiza visão do Pi com visão de Pj)
            VectorClock relogioRemetente = mc.mc.get(msg.sender);
            if (relogioRemetente != null) {
                relogioRemetente.copy(msg.vc);
                System.out.println("Relógio do remetente " + msg.sender + " atualizado para: " + relogioRemetente);
            }

            // Se i ≠ j então MC_i[i][j] = MC_i[i][j] + 1 (mais 1 msg de Pj entregue)
            if (!msg.sender.equals(ip)) {
                VectorClock meuRelogio = mc.mc.get(ip);
                if (meuRelogio != null) {
                    meuRelogio.incrementOne(msg.sender);
                    System.out.println("Meu relógio atualizado após receber de " + msg.sender + ": " + meuRelogio);
                }
            }

            // Entrega mensagem para camada superior
            cliente.deliver(msg.message);

            // Verifica mensagens para descartar
            verificarMensagensParaDescartar();

            // Exibe estado atual
            imprimirEstadoSistema();

        } catch (Exception e) {
            System.err.println("Erro ao processar mensagem recebida: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void verificarMensagensParaDescartar() {
        Iterator<Message> it = bufferMensagens.iterator();
        boolean descartou = false;

        while (it.hasNext()) {
            Message mensagem = it.next();
            Integer tempoMinimo = mc.getMinTimeOfColumn(mensagem.sender);

            if (mensagem.getTime() <= tempoMinimo) {
                System.out.println("Descartando mensagem estável: " + mensagem.message +
                        " (tempo: " + mensagem.getTime() + ", min: " + tempoMinimo + ")");
                it.remove();
                descartou = true;
            }
        }

        if (descartou) {
            System.out.println("Mensagens estáveis removidas do buffer.");
        }
    }

    private void enviarAnuncioMulticast() {
        String anuncio = "JOIN:" + ip + ":" + porta;
        byte[] buffer = anuncio.getBytes();
        DatagramPacket pacote = new DatagramPacket(buffer, buffer.length, grupoMulticast, 54321);
        try {
            socketMulticast.send(pacote);
            System.out.println("DEBUG: Anúncio enviado: " + anuncio);
        } catch (IOException e) {
            System.err.println("Erro ao enviar anúncio multicast: " + e.getMessage());
        }
    }

    private void escutarMulticast() {
        byte[] buffer = new byte[1024];
        DatagramPacket pacote = new DatagramPacket(buffer, buffer.length);

        System.out.println("Escutando anúncios multicast na porta 54321...");

        while (true) {
            try {
                socketMulticast.receive(pacote);
                String mensagem = new String(pacote.getData(), 0, pacote.getLength());

                if (mensagem.startsWith("JOIN:")) {
                    String[] partes = mensagem.split(":");
                    if (partes.length >= 3) {
                        String novoParticipante = partes[1] + ":" + partes[2];
                        String novoIp = partes[1];

                        // Verifica se não é este próprio processo e se já não está na lista
                        if (!participantes.contains(novoParticipante) && !novoIp.equals(ip)) {
                            participantes.add(novoParticipante);

                            // Adiciona ao relógio matricial se ainda não existe
                            if (!mc.mc.containsKey(novoIp)) {
                                mc.addNewParticipant(novoIp);
                            }

                            System.out.println("Novo participante descoberto: " + novoParticipante);
                            System.out.println("Total de participantes: " + participantes.size());

                            // Responde com próprio anúncio para garantir descoberta mútua
                            enviarAnuncioMulticast();
                        }
                    }
                }
            } catch (SocketTimeoutException e) {
                // Timeout é normal, continua escutando
            } catch (IOException e) {
                System.err.println("Erro no listener multicast: " + e.getMessage());
            }
        }
    }

    private boolean enviarMensagemUnicast(String mensagem, String participanteCompleto) {
        try {
            // Extrai IP e porta do formato "ip:porta"
            String[] partes = participanteCompleto.split(":");
            if (partes.length != 2) {
                System.err.println("✗ Formato inválido de participante: " + participanteCompleto);
                return false;
            }

            String ipDestino = partes[0];
            int portaDestino = Integer.parseInt(partes[1]);

            System.out.println("\n📤 Enviando mensagem unicast:");
            System.out.println("   - Para: " + ipDestino + ":" + portaDestino);
            System.out.println("   - Conteúdo: " + mensagem);
            System.out.println("   - Tamanho: " + mensagem.getBytes().length + " bytes");

            InetAddress endereco = InetAddress.getByName(ipDestino);
            byte[] buffer = mensagem.getBytes();

            DatagramPacket pacote = new DatagramPacket(buffer, buffer.length, endereco, portaDestino);
            socketUnicast.send(pacote);

            System.out.println("   ✓ Enviado com sucesso!");
            return true;

        } catch (IOException e) {
            System.err.println("   ✗ Erro de I/O ao enviar para " + participanteCompleto + ": " + e.getMessage());
            return false;
        } catch (NumberFormatException e) {
            System.err.println("   ✗ Erro no formato da porta em: " + participanteCompleto);
            return false;
        } catch (Exception e) {
            System.err.println("   ✗ Erro inesperado ao enviar para " + participanteCompleto + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void escutarUnicast() throws Exception {
        byte[] buffer = new byte[2048]; // Buffer maior para mensagens
        DatagramPacket pacote = new DatagramPacket(buffer, buffer.length);

        System.out.println("✓ Escutando mensagens unicast na porta " + porta + "...");

        while (true) {
            try {
                socketUnicast.receive(pacote);
                String mensagem = new String(pacote.getData(), 0, pacote.getLength());

                System.out.println("\n🔄 DEBUG: Pacote UDP recebido!");
                System.out.println("   - Origem: " + pacote.getAddress() + ":" + pacote.getPort());
                System.out.println("   - Tamanho: " + pacote.getLength() + " bytes");
                System.out.println("   - Conteúdo: " + mensagem);

                // Processa apenas mensagens que não são de JOIN
                if (!mensagem.startsWith("JOIN:")) {
                    System.out.println("   - Tipo: MENSAGEM MULTICAST");
                    receberMensagem(mensagem);
                } else {
                    System.out.println("   - Tipo: JOIN (ignorado via unicast)");
                }
            } catch (SocketTimeoutException e) {
                // Timeout é normal, continua escutando
            } catch (IOException e) {
                System.err.println("Erro ao receber unicast: " + e.getMessage());
            }
        }
    }

    private void imprimirEstadoSistema() {
        System.out.println("\n=== Estado do Sistema ===");
        System.out.println("IP Local: " + ip + ":" + porta);
        System.out.println("Sistema Inicializado: " + sistemaInicializado.get());
        System.out.println("Participantes Ativos (" + participantes.size() + "):");
        for (String participante : participantes) {
            if (participante.startsWith(ip + ":")) {
                System.out.println("  - " + participante + " (EU)");
            } else {
                System.out.println("  - " + participante);
            }
        }

        System.out.println("\nBuffer de Mensagens (" + bufferMensagens.size() + "):");
        if (bufferMensagens.isEmpty()) {
            System.out.println("  (vazio)");
        } else {
            for (Message msg : bufferMensagens) {
                System.out.println("  - \"" + msg.message + "\" (de " + msg.sender +
                        ", tempo: " + msg.getTime() + ")");
            }
        }

        System.out.println("\nRelógio Matricial:");
        System.out.println(mc.toString());
        System.out.println("========================\n");
    }
}