package network;

import java.net.ServerSocket;
import java.net.Socket;

import lang.Lang;
import ntp.NTP;

import org.apache.log4j.Logger;

import qora.transaction.Transaction;
import settings.Settings;
import controller.Controller;

public class ConnectionAcceptor extends Thread{

	private ConnectionCallback callback;
	
	private static final Logger LOGGER = Logger
			.getLogger(ConnectionAcceptor.class);
	private ServerSocket socket;
	
	private boolean isRun;
	
	public ConnectionAcceptor(ConnectionCallback callback)
	{
		this.callback = callback;
	}
	
	public void run()
	{
		this.isRun = true;
		while(isRun)
		{
			try
			{	
				if(socket == null)
				{
					//START LISTENING
					socket = new ServerSocket(Controller.getInstance().getNetworkPort()); 
				}
				
				
				//CHECK IF WE HAVE MAX CONNECTIONS CONNECTIONS
				if(Settings.getInstance().getMaxConnections() <= callback.getActiveConnections().size())
				{
					//IF SOCKET IS OPEN CLOSE IT
					if(!socket.isClosed())
					{
						socket.close();
					}
					
					Thread.sleep(100);
				}
				else
				{		
					//REOPEN SOCKET
					if(socket.isClosed())
					{
						socket = new ServerSocket(Controller.getInstance().getNetworkPort()); 
					}
					
					//ACCEPT CONNECTION
					Socket connectionSocket = socket.accept();
					
					//CHECK IF SOCKET IS NOT LOCALHOST || WE ARE ALREADY CONNECTED TO THAT SOCKET || BLACKLISTED
					if(
							/*connectionSocket.getInetAddress().isSiteLocalAddress() 
							 * || connectionSocket.getInetAddress().isAnyLocalAddress() 
							 * || connectionSocket.getInetAddress().isLoopbackAddress() 
							 *  */
							(
									(NTP.getTime() < Transaction.getPOWFIX_RELEASE() ) 
									&& 
									callback.isConnectedTo(connectionSocket.getInetAddress())
							)
							||
							PeerManager.getInstance().isBlacklisted(connectionSocket.getInetAddress()))
					{
						//DO NOT CONNECT TO OURSELF/EXISTING CONNECTION
						connectionSocket.close();
					}
					else
					{
						//CREATE PEER
						new Peer(callback, connectionSocket);
					}
				}
			}
			catch(Exception e)
			{
				LOGGER.error(e.getMessage(),e);
				LOGGER.warn(Lang.getInstance().translate("Error accepting new connection"));			
			}
		}
	}
	
	public void halt()
	{
		this.isRun = false;
	}
}
