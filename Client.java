import StableMulticast.*;
import java.util.Scanner;

public class Client implements IStableMulticast
{
    private static StableMulticast stableMulticast;
    private static Client client;
    private static String meuIp;
    private static int minhaPorta = 2020; // Porta padrão

    public static void main(String[] args)
    {
        Scanner scanner = new Scanner(System.in);

        // Permite especificar IP e porta via argumentos
        if (args.length >= 1) {
            meuIp = args[0];
        } else {
            System.out.print("Digite o IP deste processo (ex: 192.168.0.1 ou 127.0.0.1): ");
            meuIp = scanner.nextLine().trim();

            // Se não digitou nada, usa localhost
            if (meuIp.isEmpty()) {
                meuIp = "127.0.0.1";
                System.out.println("Usando IP padrão: " + meuIp);
            }
        }

        if (args.length >= 2) {
            try {
                minhaPorta = Integer.parseInt(args[1]);
                System.out.println("Usando porta especificada: " + minhaPorta);
            } catch (NumberFormatException e) {
                System.out.println("Porta inválida, usando porta padrão: " + minhaPorta);
            }
        } else {
            System.out.print("Digite a porta (padrão " + minhaPorta + "): ");
            String portaStr = scanner.nextLine().trim();
            if (!portaStr.isEmpty()) {
                try {
                    minhaPorta = Integer.parseInt(portaStr);
                } catch (NumberFormatException e) {
                    System.out.println("Porta inválida, usando porta padrão: " + minhaPorta);
                }
            }
        }

        System.out.println("\n=== Iniciando Cliente Multicast Estável ===");
        System.out.println("IP: " + meuIp);
        System.out.println("Porta: " + minhaPorta);
        System.out.println("Inicializando middleware...");

        try {
            client = new Client();
            stableMulticast = new StableMulticast(meuIp, minhaPorta, client);

            System.out.println("✓ Middleware inicializado com sucesso!");
            System.out.println("Aguardando descoberta de outros participantes...");
            System.out.println("(Este processo pode levar até 15 segundos)");

            // Aguarda tempo suficiente para descoberta
            Thread.sleep(16000); // 16 segundos para garantir descoberta completa

            System.out.println("\n=== Sistema Pronto ===");
            mostrarAjuda();

            // Loop principal para interação do usuário
            while (true) {
                System.out.print("\n> Digite uma mensagem (ou comando): ");
                String entrada = scanner.nextLine().trim();

                if (entrada.equalsIgnoreCase("quit") || entrada.equalsIgnoreCase("sair")) {
                    System.out.println("Encerrando cliente...");
                    System.exit(0);
                } else if (entrada.equalsIgnoreCase("help") || entrada.equalsIgnoreCase("ajuda")) {
                    mostrarAjuda();
                } else if (entrada.equalsIgnoreCase("status") || entrada.equalsIgnoreCase("estado")) {
                    System.out.println("Solicitando status do sistema...");
                    // O status será mostrado automaticamente
                } else if (entrada.equalsIgnoreCase("clear") || entrada.equalsIgnoreCase("limpar")) {
                    // Limpa a tela (funciona em alguns terminais)
                    for (int i = 0; i < 50; i++) System.out.println();
                    System.out.println("=== Tela Limpa ===");
                } else if (!entrada.isEmpty()) {
                    try {
                        stableMulticast.msend(entrada, client);
                    } catch (Exception e) {
                        System.err.println("Erro ao enviar mensagem: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Erro fatal ao inicializar sistema: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void mostrarAjuda() {
        System.out.println("\n=== Comandos Disponíveis ===");
        System.out.println("• Digite qualquer mensagem para enviar via multicast");
        System.out.println("• 'help' ou 'ajuda' - mostra esta ajuda");
        System.out.println("• 'status' ou 'estado' - força exibição do estado atual");
        System.out.println("• 'clear' ou 'limpar' - limpa a tela");
        System.out.println("• 'quit' ou 'sair' - encerra o programa");

        System.out.println("\n=== Como Usar ===");
        System.out.println("1. Execute múltiplos clientes (em terminais diferentes)");
        System.out.println("2. Aguarde a descoberta automática (até 15 segundos)");
        System.out.println("3. Digite uma mensagem e pressione Enter");
        System.out.println("4. Escolha se quer enviar para todos ou selecionar destinatários");

        System.out.println("\n=== Exemplo de Uso ===");
        System.out.println("Terminal 1: java Client 127.0.0.1 2020");
        System.out.println("Terminal 2: java Client 127.0.0.1 2021");
        System.out.println("Terminal 3: java Client 127.0.0.1 2022");

        System.out.println("\n=== Informações do Sistema ===");
        System.out.println("IP Local: " + meuIp);
        System.out.println("Porta: " + minhaPorta);
    }

    @Override
    public void deliver(String msg)
    {
        System.out.println("\n🔔 *** MENSAGEM ENTREGUE: \"" + msg + "\" ***");
        System.out.print("> "); // Reimprime o prompt
    }
}