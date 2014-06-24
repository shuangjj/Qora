package qora;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

import network.Peer;
import network.message.BlockMessage;
import network.message.Message;
import network.message.MessageFactory;
import network.message.SignaturesMessage;
import network.message.TransactionMessage;
import qora.block.Block;
import qora.transaction.Transaction;
import utils.ObserverMessage;
import database.DatabaseSet;

public class Synchronizer extends Observable
{
	private boolean run = true;
	
	public List<Transaction> synchronize(DatabaseSet db, Block lastCommonBlock, List<Block> newBlocks) throws Exception
	{
		List<Transaction> orphanedTransactions = new ArrayList<Transaction>();
		
		//VERIFY ALL BLOCKS TO PREVENT ORPHANING INCORRECTLY
		DatabaseSet fork = db.fork();	
		
		//ORPHAN BLOCK IN FORK TO VALIDATE THE NEW BLOCKS
		if(lastCommonBlock != null)
		{
			//GET LAST BLOCK
			Block lastBlock = fork.getBlockDatabase().getLastBlock();
			
			//ORPHAN LAST BLOCK UNTIL WE HAVE REACHED COMMON BLOCK
			while(!Arrays.equals(lastBlock.getSignature(), lastCommonBlock.getSignature()))
			{
				lastBlock.orphan(fork);
				lastBlock = fork.getBlockDatabase().getLastBlock();
			}
		}
		
		//VALIDATE THE NEW BLOCKS
		for(Block block: newBlocks)
		{
			//CHECK IF VALID
			if(block.isValid(fork))
			{
				//PROCESS TO VALIDATE NEXT BLOCKS
				block.process(fork);
			}
			else
			{
				//INVALID BLOCK THROW EXCEPTION
				throw new Exception("Dishonest peer");
			}
		}
		
		//NEW BLOCKS ARE ALL VALID SO WE CAN ORPHAN THEM FOR REAL NOW
		if(lastCommonBlock != null)
		{
			//GET LAST BLOCK
			Block lastBlock = db.getBlockDatabase().getLastBlock();
			
			//ORPHAN LAST BLOCK UNTIL WE HAVE REACHED COMMON BLOCK
			while(!Arrays.equals(lastBlock.getSignature(), lastCommonBlock.getSignature()))
			{
				//ADD ORPHANED TRANSACTIONS
				orphanedTransactions.addAll(lastBlock.getTransactions());
				
				lastBlock.orphan(db);
				this.notifyOrphan(db, lastBlock);
				lastBlock = db.getBlockDatabase().getLastBlock();
			}
		}
		
		//PROCESS THE NEW BLOCKS
		for(Block block: newBlocks)
		{
			//SYNCHRONIZED PROCESSING
			this.process(block);
			//block.process(db);
			//this.notifyProcess(db, block);
		}	
		
		return orphanedTransactions;
	}
	
	public void synchronize(Peer peer) throws Exception
	{
		//FIND LAST COMMON BLOCK
		Block common =  this.findLastCommonBlock(peer);
				
		//CHECK COMMON BLOCK EXISTS
		List<byte[]> signatures;
		if(Arrays.equals(common.getSignature(), DatabaseSet.getInstance().getBlockDatabase().getLastBlockSignature()))
		{
			//GET NEXT 500 SIGNATURES
			signatures = this.getBlockSignatures(common, BlockChain.MAX_SIGNATURES, peer);
			
			//CREATE BLOCK BUFFER
			BlockBuffer blockBuffer = new BlockBuffer(signatures, peer);
			
			//GET AND PROCESS BLOCK BY BLOCK
			for(byte[] signature: signatures)
			{
				//INTERRUPT
				if(!this.run)
				{
					return;
				}
				
				//GET BLOCK
				Block block = blockBuffer.getBlock(signature);
				
				if(block.isValid())
				{
					//PROCESS BLOCK
					this.process(block);
				}
				else
				{
					//INVALID BLOCK THROW EXCEPTION
					throw new Exception("Dishonest peer");
				}
			}
			
			//STOP BLOCKBUFFER
			blockBuffer.stopThread();
		}
		else
		{
			//GET SIGNATURES FROM COMMON HEIGHT UNTIL CURRENT HEIGHT
			signatures = this.getBlockSignatures(common, DatabaseSet.getInstance().getBlockDatabase().getLastBlock().getHeight() - common.getHeight(), peer);	
			
			//GET THE BLOCKS FROM SIGNATURES
			List<Block> blocks = this.getBlocks(signatures, peer);
							
			//SYNCHRONIZE BLOCKS
			List<Transaction> orphanedTransactions = this.synchronize(DatabaseSet.getInstance(), common, blocks);
			
			//SEND ORPHANED TRANSACTIONS TO PEER
			for(Transaction transaction: orphanedTransactions)
			{
				TransactionMessage transactionMessage = new TransactionMessage(transaction);
				peer.sendMessage(transactionMessage);
			}
		}
	}
	
	private List<byte[]> getBlockSignatures(Block start, int amount, Peer peer) throws Exception
	{
		//ASK NEXT 500 HEADERS SINCE START
		List<byte[]> headers = this.getBlockSignatures(start.getSignature(), peer);
		List<byte[]> nextHeaders;
		if(headers.size() > 0 && headers.size() < amount)
		{
			do
			{
				nextHeaders = this.getBlockSignatures(headers.get(headers.size()-1), peer);
				headers.addAll(nextHeaders);
			}
			while(headers.size() < amount && nextHeaders.size() > 0);
		}
		
		return headers;
	}
	
	private List<byte[]> getBlockSignatures(byte[] header, Peer peer) 
	{
		///CREATE MESSAGE
		Message message = MessageFactory.getInstance().createGetHeadersMessage(header);
		
		//SEND MESSAGE TO PEER
		SignaturesMessage response = (SignaturesMessage) peer.getResponse(message);
		
		return response.getSignatures();
	}
	
	private Block findLastCommonBlock(Peer peer) throws Exception
	{
		Block block = DatabaseSet.getInstance().getBlockDatabase().getLastBlock();
		
		//GET HEADERS UNTIL COMMON BLOCK IS FOUND OR ALL BLOCKS HAVE BEEN CHECKED
		List<byte[]> headers = this.getBlockSignatures(block.getSignature(), peer);
		while(headers.size() == 0 && block.getHeight() > 1)
		{
			//GO 500 BLOCKS BACK
			for(int i=0; i<BlockChain.MAX_SIGNATURES && block.getHeight() > 1; i++)
			{
				block = block.getParent();
			}
			
			headers = this.getBlockSignatures(block.getSignature(), peer);
		}
		
		//CHECK IF NO HEADERS FOUND EVEN AFTER CHECKING WITH THE GENESISBLOCK
		if(headers.size() == 0)
		{
			throw new Exception("Dishonest peer");
		}
		
		//FIND LAST COMMON BLOCK IN HEADERS
		for(int i=headers.size()-1; i>=0; i--)
		{
			//CHECK IF WE KNOW BLOCK
			if(DatabaseSet.getInstance().getBlockDatabase().containsBlock(headers.get(i)))
			{
				return DatabaseSet.getInstance().getBlockDatabase().getBlock(headers.get(i));
			}
		}
		
		return block;
	}

	private List<Block> getBlocks(List<byte[]> signatures, Peer peer) throws Exception {
		
		List<Block> blocks = new ArrayList<Block>();
		
		for(byte[] signature: signatures)
		{
			//ADD TO LIST
			blocks.add(this.getBlock(signature, peer));	
		}
		
		return blocks;
	}
	
	private Block getBlock(byte[] signature, Peer peer) throws Exception
	{
		//CREATE MESSAGE
		Message message = MessageFactory.getInstance().createGetBlockMessage(signature);
		
		//SEND MESSAGE TO PEER
		BlockMessage response = (BlockMessage) peer.getResponse(message);
		
		//CHECK IF WE GOT RESPONSE
		if(response == null)
		{
			//ERROR
			throw new Exception("Peer timed out");
		}
		
		//CHECK BLOCK SIGNATURE
		if(!response.getBlock().isSignatureValid())
		{
			throw new Exception("Invalid block");
		}
		
		//ADD TO LIST
		return response.getBlock();
	}
	
	private void notifyOrphan(DatabaseSet db, Block block)
	{
		//T NOTIFY
		this.setChanged();
		this.notifyObservers(new ObserverMessage(ObserverMessage.REMOVE_BLOCK_TYPE, block));
		this.setChanged();
		this.notifyObservers(new ObserverMessage(ObserverMessage.LIST_BLOCK_TYPE, this.getLastBlocks(db)));
	}
	
	//SYNCHRONIZED DO NOT PROCCESS A BLOCK AT THE SAME TIME
	public synchronized void process(Block block) 
	{
		//SYNCHRONIZED MIGHT HAVE BEEN PROCESSING PREVIOUS BLOCK
		if(block.isValid())
		{
			//PROCESS
			block.process();
			
			//NOTIFY
			notifyProcess(DatabaseSet.getInstance(), block);
		}
	}

	private void notifyProcess(DatabaseSet db, Block block) 
	{	
		//NOTIFY
		this.setChanged();
		this.notifyObservers(new ObserverMessage(ObserverMessage.ADD_BLOCK_TYPE, block));
		
		this.setChanged();
		this.notifyObservers(new ObserverMessage(ObserverMessage.LIST_BLOCK_TYPE, this.getLastBlocks(db)));
		
		this.setChanged();
		this.notifyObservers(new ObserverMessage(ObserverMessage.LIST_TRANSACTION_TYPE, DatabaseSet.getInstance().getTransactionsDatabase().getTransactions()));
	}
	
	private List<Block> getLastBlocks(DatabaseSet db)
	{
		//GET 50 LAST BLOCKS
		List<Block> blocks = new ArrayList<Block>();	
		blocks.add(db.getBlockDatabase().getLastBlock());
		
		Block parent;
		while(blocks.size() < 50 && (parent = blocks.get(blocks.size() - 1).getParent()) != null)
		{
			blocks.add(parent);
		}
	
		return blocks;
	}
	
	@Override
	public void addObserver(Observer o)
	{
		super.addObserver(o);
		
		//SEND LAST BLOCKS ON REGISTER
		o.update(this, new ObserverMessage(ObserverMessage.LIST_BLOCK_TYPE, this.getLastBlocks(DatabaseSet.getInstance())));
	}

	public void stop() {
		this.run = false;
	}
}