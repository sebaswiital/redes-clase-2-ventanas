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
 * y puede difundir broadcast mensajes a todos los clientes conectados.
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
    // provocar errores de concurrencia
    //el final es para que no se vaya a volver a establecer una lista asì ya que solo puede hacer una, pero no la hace inmodificable
    private final List<ManejadorCliente> clientesConectados = new CopyOnWriteArrayList<>();


    public PrincipalSrv() {
        super("Servidor Bacano");
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
        etiquetaEstado = new JLabel("Iniciando server");
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


    private void iniciarServidor() {
        // Se usa un hilo aparte para que ServerSocket.accept() (que se queda
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
// esta funcion la copiamos y permite dejar de repetir el areaLog.append sino solo llamar la funcion cuando se quiera ver un texto
    private void registrarEnLog(String texto) {
        SwingUtilities.invokeLater(() -> areaLog.append(texto + "\n"));
    }

   //envia un mensaje a la gente que este conectada en el momento
    private void difundirMensaje(String mensaje) {
        registrarEnLog(mensaje);
        for (ManejadorCliente cliente : clientesConectados) {
            cliente.enviar(mensaje);
        }
    }


    //este manejador es el que permite atender a los cliente en sus hilos para no interferir, eL RUNNABLE es para que esta clase se
    //maneje en su propio hilo, que no interfiera con las operaciones principales.
    private class ManejadorCliente implements Runnable {
        //el socket
        private final Socket socket;
        //print writer sirve para mandar mensajes a los que esten conectados
        private PrintWriter salida;
        //el buffered reader sirve para leer los mensajes
        private BufferedReader entrada;

        //constructor de la clase en una linea nomas
        public ManejadorCliente(Socket socket) {
            this.socket = socket;
        }

        @Override
        //como parte del runnable, esto se va a ejecutar en un hilo aparte
        public void run() {
            try {
                //agarra la informacion que envio el cliente con socket.getInputStream y lo mete en un bufferedreader pa leerlo
                entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                //prepara el texto para enviar, el autoflush con ese TRUE e para que el mensaje salga de inmediato, que no se acumule.
                salida = new PrintWriter(socket.getOutputStream(), true);

                String lineaRecibida;
                while ((lineaRecibida = entrada.readLine()) != null) { //entra en un while esperando a que el cliente le de enter para que "lineaRecibida" tenga valor diferende de null.
                    String mensajeCompleto = "[" + socket.getInetAddress().getHostAddress() + "]: " + lineaRecibida;
                    difundirMensaje(mensajeCompleto); // se reenvía el texto que envien a TODOS los clientes,
                }
            } catch (IOException ex) {
                registrarEnLog("Cliente desconectado: " + socket.getInetAddress().getHostAddress());
            } finally {// este finally como manejo de errores es para quitar a los clientes que se desconecten del servidor
                clientesConectados.remove(this);
                try {
                    socket.close();// y cerramos el socket para que no estemos enviando cosas a clientes que no existen
                } catch (IOException ignored) {
                }
            }
        }

        //funcion que muestra el mensaje que enviamos en LOG
        public void enviar(String mensaje) {
            if (salida != null) {
                salida.println(mensaje);
            }
        }


    }

    //funcion de los botones, con enviar.
    private void alPresionarEnviar(ActionEvent e) {
        String mensaje = campoMensaje.getText().trim();

        //mientras el mensaje no estè vacio entonces el servidor podra enviar el mensaje con la funcion "difundir mensaje"
        if (!mensaje.isEmpty()) {
            difundirMensaje("SERVIDOR: " + mensaje);
            campoMensaje.setText("");
        }
    }


    public static void main(String[] args) {
        SwingUtilities.invokeLater(PrincipalSrv::new);
    }
}
