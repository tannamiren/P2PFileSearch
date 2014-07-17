import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

public class Connector extends Thread {
    public Node _node_ref;
    public int _total_neighbours = 0;
    // _neighbour has k:v pairs as node_id : NodeInfo
    //public ConcurrentHashMap<Integer,NodeInfo> _neighbours;

    // _node_lookup has k:v pairs as "ip:port" : node_id
    public ConcurrentHashMap<String,Integer> _node_lookup;

    // _cli_socks and _writers have node ids as keys
    public ConcurrentHashMap<Integer,Socket> _cli_socks;
    public ConcurrentHashMap<Integer,OutputStream> _outstreams;

    public Connector(Node n) {
        _node_ref = n;
        //_neighbours = new ConcurrentHashMap<>();
        _node_lookup = new ConcurrentHashMap<>();
        _cli_socks = new ConcurrentHashMap<>();
        _outstreams = new ConcurrentHashMap<>();
    }

    public void send_message(Message msg, NodeInfo n) {
        if (!_node_lookup.containsKey(n.toString())) {
            System.err.println("node "+n+" not in _node_lookup");
            return;
        }
        try {
            ObjectOutputStream stream = new ObjectOutputStream(_outstreams.get(_node_lookup.get(n.toString())));
            stream.writeObject(msg);
            stream.flush();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void deliver_msg(Message msg) {
        if(msg.getType().equals("join")) {
            System.out.println("join from "+msg.getSender());
            neighbour_joined(msg.getSender());
        }
        else if (msg.getType().equals("search")) {
            _node_ref.process_msg(msg);
        }
    }

    public synchronized void neighbour_joined(NodeInfo n) {
        System.out.println(n);
        try {
            Socket sock = new Socket(n.ip, n.port);
            OutputStream outstream = sock.getOutputStream();

            int id = _total_neighbours+1;
            _total_neighbours++;

            _node_lookup.put(n.toString(),id);
            _cli_socks.put(id,sock);
            _outstreams.put(id,outstream);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
    public synchronized void join_neighbour(NodeInfo n) {
        if (_node_lookup.containsKey(n.toString())) {
            System.err.println("connected to"+n.ip);
            System.out.print("> ");
            return;
        }
        try {
            Socket sock = new Socket(n.ip, n.port);
            OutputStream outstream = sock.getOutputStream();

            int id = _total_neighbours+1;
            _total_neighbours++;

            _node_lookup.put(n.toString(),id);
            _cli_socks.put(id,sock);
            _outstreams.put(id,outstream);

            ObjectOutputStream stream = new ObjectOutputStream(_outstreams.get(_node_lookup.get(n.toString())));
            Message msg = new Message.MessageBuilder()
                    .from(_node_ref._info)
                    .to(n)
                    .type("join").build();

            stream.writeObject(msg);
            stream.flush();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
    @Override
    public void run() {
        try (ServerSocket server = new ServerSocket(_node_ref._info.port)) {
            while(true) {
                Socket client = server.accept();
                Thread listener = new ListenerThread(client,this);
                listener.start();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
