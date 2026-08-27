package org.vinni.servidor.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.net.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Servidor TCP con GUI en Swing.
 * Acepta MÚLTIPLES clientes al mismo tiempo (cada uno en su propio hilo)
 * y puede difundir (broadcast) mensajes a todos los clientes conectados.
 */
public class PrincipalSrv extends JFrame {

    private static final int PUERTO = 12345;
//reorganizamos el jFRAME con una IA para que se vea un poco mejor y haya mas espacio para leer los mensajes de los clientes.
    private JTextArea areaLog;
    private JTextField campoMensaje;
    private JButton botonEnviar;
    private JLabel etiquetaEstado;

    private ServerSocket serverSocket;

    // Lista thread-safe: varios hilos (uno por cliente) pueden leer/escribir aquí sin
    // provocar errores de concurrencia (ConcurrentModificationException, etc.)
    private final List<ManejadorCliente> clientesConectados = new CopyOnWriteArrayList<>();


    public PrincipalSrv() {
        super("Servidor Multicliente");
        construirInterfaz();
        iniciarServidor();
    }

    private void construirInterfaz() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(520, 420);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        areaLog = new JTextArea();
        areaLog.setEditable(false);
        JScrollPane scroll = new JScrollPane(areaLog);

        campoMensaje = new JTextField();
        botonEnviar = new JButton("Enviar a todos");
        etiquetaEstado = new JLabel("Iniciando servidor...");
        etiquetaEstado.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JPanel panelInferior = new JPanel(new BorderLayout());
        panelInferior.add(campoMensaje, BorderLayout.CENTER);
        panelInferior.add(botonEnviar, BorderLayout.EAST);

        add(etiquetaEstado, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(panelInferior, BorderLayout.SOUTH);

        botonEnviar.addActionListener(this::alPresionarEnviar);
        campoMensaje.addActionListener(this::alPresionarEnviar);

        setVisible(true);
    }

    private void alPresionarEnviar(ActionEvent e) {
        String mensaje = campoMensaje.getText().trim();
        if (!mensaje.isEmpty()) {
            difundirMensaje("SERVIDOR: " + mensaje);
            campoMensaje.setText("");
        }
    }

    private void iniciarServidor() {
        // Se usa un hilo aparte para que ServerSocket.accept() (que se queda "congelado"
        // esperando conexiones) no bloquee la interfaz gráfica.
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PUERTO);

                String ipLocal = InetAddress.getLocalHost().getHostAddress();
                SwingUtilities.invokeLater(() ->
                        etiquetaEstado.setText("Escuchando en " + ipLocal + " : " + PUERTO));
                registrarEnLog("Servidor iniciado. Los clientes deben conectarse a "
                        + ipLocal + ":" + PUERTO);

                while (true) {
                    Socket socketCliente = serverSocket.accept(); // espera hasta que llegue un cliente nuevo
                    registrarEnLog("Nueva conexión desde: " + socketCliente.getInetAddress().getHostAddress());

                    ManejadorCliente manejador = new ManejadorCliente(socketCliente);
                    clientesConectados.add(manejador);
                    new Thread(manejador).start(); // cada cliente vive en su propio hilo
                }
            } catch (IOException ex) {
                registrarEnLog("Error en el servidor: " + ex.getMessage());
            }
        }).start();
    }

    private void registrarEnLog(String texto) {
        SwingUtilities.invokeLater(() -> areaLog.append(texto + "\n"));
    }

    /** Envía un mensaje a TODOS los clientes conectados actualmente. */
    private void difundirMensaje(String mensaje) {
        registrarEnLog(mensaje);
        for (ManejadorCliente cliente : clientesConectados) {
            cliente.enviar(mensaje);
        }
    }

    /**
     * Representa la conexión con UN cliente.
     * Cada instancia corre en su propio hilo (implements Runnable) para poder
     * leer sus mensajes sin bloquear a los demás clientes ni al servidor.
     */
    private class ManejadorCliente implements Runnable {
        private final Socket socket;
        private PrintWriter salida;
        private BufferedReader entrada;

        public ManejadorCliente(Socket socket) {
            this.socket = socket;
        }

        public void enviar(String mensaje) {
            if (salida != null) {
                salida.println(mensaje);
            }
        }

        @Override
        public void run() {
            try {
                entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                salida = new PrintWriter(socket.getOutputStream(), true);

                String lineaRecibida;
                while ((lineaRecibida = entrada.readLine()) != null) {
                    String mensajeCompleto = "[" + socket.getInetAddress().getHostAddress() + "]: " + lineaRecibida;
                    difundirMensaje(mensajeCompleto); // se reenvía a TODOS los clientes, no solo al que lo mandó
                }
            } catch (IOException ex) {
                registrarEnLog("Cliente desconectado: " + socket.getInetAddress().getHostAddress());
            } finally {
                clientesConectados.remove(this);
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(PrincipalSrv::new);
    }
}
