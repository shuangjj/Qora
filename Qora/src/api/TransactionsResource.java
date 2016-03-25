package api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import controller.Controller;
import database.DBSet;
import qora.account.Account;
import qora.block.Block;
import qora.crypto.Base58;
import qora.crypto.Crypto;
import qora.transaction.Transaction;
import utils.APIUtils;
import utils.Pair;

@Path("transactions")
@Produces(MediaType.APPLICATION_JSON)
public class TransactionsResource {

	
	private static final Logger LOGGER = Logger
			.getLogger(TransactionsResource.class);
	
	@Context
	HttpServletRequest request;

	@GET
	public String getTransactions()
	{
		return this.getTransactionsLimited(50);
	}
	
	@POST
	@Path("/process")
	public String processTransactionFromRaw(String rawDataBase58)
	{
		byte[] transactionBytes = Base58.decode(rawDataBase58);
		
		Pair<Transaction, Integer> result = Controller.getInstance().createTransactionFromRaw(transactionBytes);
		if(result.getB() == Transaction.VALIDATE_OK) {
			return result.getA().toJson().toJSONString();
		} else {
			return result.getB().toString();
		}
	}
	
	@GET
	@Path("/{address}")
	public String getTransactions(@PathParam("address") String address)
	{
		return this.getTransactionsLimited(address, 50);
	}
	
	@GET
	@Path("address/{address}")
	public String getTransactionsTwo(@PathParam("address") String address)
	{
		return this.getTransactions(address);
	}
	
	@SuppressWarnings("unchecked")
	@GET
	@Path("limit/{limit}")
	public String getTransactionsLimited(@PathParam("limit") int limit)
	{
		APIUtils.askAPICallAllowed("GET transactions/limit/" + limit, request);

		//CHECK IF WALLET EXISTS
		if(!Controller.getInstance().doesWalletExists())
		{
			throw ApiErrorFactory.getInstance().createError(ApiErrorFactory.ERROR_WALLET_NO_EXISTS);
		}
		
		//GET TRANSACTIONS
		List<Pair<Account, Transaction>> transactions = Controller.getInstance().getLastTransactions(limit);
		
		//ORGANIZE TRANSACTIONS
		Map<Account, List<Transaction>> orderedTransactions = new HashMap<Account, List<Transaction>>();
		for(Pair<Account, Transaction> transaction: transactions)
		{
			if(!orderedTransactions.containsKey(transaction.getA()))
			{
				orderedTransactions.put(transaction.getA(), new ArrayList<Transaction>());
			}
			
			orderedTransactions.get(transaction.getA()).add(transaction.getB());
		}
		
		//CREATE JSON OBJECT
		JSONArray orderedTransactionsJSON = new JSONArray();
		
		for(Account account: orderedTransactions.keySet())
		{
			JSONArray transactionsJSON = new JSONArray();
			for(Transaction transaction: orderedTransactions.get(account))
			{
				transactionsJSON.add(transaction.toJson());
			}
			
			JSONObject accountTransactionsJSON = new JSONObject();
			accountTransactionsJSON.put("account", account.getAddress());
			accountTransactionsJSON.put("transactions", transactionsJSON);
			orderedTransactionsJSON.add(accountTransactionsJSON);		
		}
		
		return orderedTransactionsJSON.toJSONString();
	}
	
	@SuppressWarnings("unchecked")
	@GET
	@Path("address/{address}/limit/{limit}")
	public String getTransactionsLimited(@PathParam("address") String address, @PathParam("limit") int limit)
	{
		APIUtils.askAPICallAllowed("GET transactions/address/" + address + "/limit/" + limit, request);

		//CHECK IF WALLET EXISTS
		if(!Controller.getInstance().doesWalletExists())
		{
			throw ApiErrorFactory.getInstance().createError(ApiErrorFactory.ERROR_WALLET_NO_EXISTS);
		}
		
		//CHECK ADDRESS
		if(!Crypto.getInstance().isValidAddress(address))
		{
			throw ApiErrorFactory.getInstance().createError(ApiErrorFactory.ERROR_INVALID_ADDRESS);
		}
		
		//CHECK ACCOUNT IN WALLET
		Account account = Controller.getInstance().getAccountByAddress(address);	
		if(account == null)
		{
			throw ApiErrorFactory.getInstance().createError(ApiErrorFactory.ERROR_WALLET_ADDRESS_NO_EXISTS);
		}
		
		JSONArray array = new JSONArray();
		for(Transaction transaction: Controller.getInstance().getLastTransactions(account, limit))
		{
			array.add(transaction.toJson());
		}
		
		return array.toJSONString();
	}
	
	@GET
	@Path("signature/{signature}")
	public static String getTransactionsBySignature(@PathParam("signature") String signature) throws Exception
	{
		//DECODE SIGNATURE
		byte[] signatureBytes;
		try
		{
			signatureBytes = Base58.decode(signature);
		}
		catch(Exception e)
		{
			throw ApiErrorFactory.getInstance().createError(ApiErrorFactory.ERROR_INVALID_SIGNATURE);
		}
		
		//GET TRANSACTION
		Transaction transaction = Controller.getInstance().getTransaction(signatureBytes);
		
		//CHECK IF TRANSACTION EXISTS
		if(transaction == null)
		{
			throw ApiErrorFactory.getInstance().createError(ApiErrorFactory.ERROR_TRANSACTION_NO_EXISTS);
		}
		
		return transaction.toJson().toJSONString();
	}
	
	@SuppressWarnings("unchecked")
	@GET
	@Path("/network")
	public String getNetworkTransactions()
	{
		List<Transaction> transactions = Controller.getInstance().getUnconfirmedTransactions();
		JSONArray array = new JSONArray();
		
		for(Transaction transaction: transactions)
		{
			array.add(transaction.toJson());
		}
		
		return array.toJSONString();
	}
	
	@SuppressWarnings("unchecked")
	@GET
	@Path("/unconfirmedof/{address}")
	public String getNetworkTransactions(@PathParam("address") String address)
	{
		List<Transaction> transactions = Controller.getInstance().getUnconfirmedTransactions();
		JSONArray array = new JSONArray();
		
		for(Transaction transaction: transactions)
		{
			if(transaction.getCreator().getAddress().equals(address))
			array.add(transaction.toJson());
		}
		
		return array.toJSONString();
	}
	
	@SuppressWarnings("unchecked")
	@POST
	@Path("/scan")
	public String scanTransactions(String x)
	{
		try
		{
			//READ JSON
			JSONObject jsonObject = (JSONObject) JSONValue.parse(x);
			
			//GET BLOCK
			Block block = null;
			if(jsonObject.containsKey("start"))
			{
				byte[] signatureBytes;
				try
				{
					String signature = (String) jsonObject.get("start");
					signatureBytes = Base58.decode(signature);
				}
				catch(Exception e)
				{
					throw ApiErrorFactory.getInstance().createError(ApiErrorFactory.ERROR_INVALID_SIGNATURE);
				}
						
				block = Controller.getInstance().getBlock(signatureBytes);
				
				//CHECK IF BLOCK EXISTS
				if(block == null)
				{
					throw ApiErrorFactory.getInstance().createError(ApiErrorFactory.ERROR_BLOCK_NO_EXISTS);
				}	
			}
			
			//CHECK FOR BLOCKLIMIT
			int blockLimit = -1;
			try
			{
				blockLimit = ((Long) jsonObject.get("blocklimit")).intValue();

				if (blockLimit > 360) // 360 ensures at least six hours of blocks can be queried at once
				{
					APIUtils.disallowRemote(request);
				}
			}
			catch(NullPointerException e)
			{
				//OPTION DOES NOT EXIST
				APIUtils.disallowRemote(request);
			}
			catch(ClassCastException e)
			{
				//JSON EXCEPTION
				throw ApiErrorFactory.getInstance().createError(ApiErrorFactory.ERROR_JSON);
			}

			//CHECK FOR TRANSACTIONLIMIT
			int transactionLimit = -1;
			try
			{
				transactionLimit = ((Long) jsonObject.get("transactionlimit")).intValue();
			}
			catch(NullPointerException e)
			{
				//OPTION DOES NOT EXIST
			}
			catch(ClassCastException e)
			{
				//JSON EXCEPTION
				throw ApiErrorFactory.getInstance().createError(ApiErrorFactory.ERROR_JSON);
			}
			
			//CHECK FOR TYPE
			int type = -1;
			try
			{
				 type = ((Long) jsonObject.get("type")).intValue();
			}
			catch(NullPointerException e)
			{
				//OPTION DOES NOT EXIST
			}
			catch(ClassCastException e)
			{
				//JSON EXCEPTION
				throw ApiErrorFactory.getInstance().createError(ApiErrorFactory.ERROR_JSON);
			}
			
			//CHECK FOR SERVICE
			int service = -1;
			try
			{
				 service = ((Long) jsonObject.get("service")).intValue();
			}
			catch(NullPointerException e)
			{
				//OPTION DOES NOT EXIST
			}
			catch(ClassCastException e)
			{
				//JSON EXCEPTION
				throw ApiErrorFactory.getInstance().createError(ApiErrorFactory.ERROR_JSON);
			}
			
			//CHECK FOR ACCOUNT
			Account account = null;
			try
			{
				if(jsonObject.containsKey("address"))
				{
					String address = (String) jsonObject.get("address");
					 
					//CHECK ADDRESS
					if(!Crypto.getInstance().isValidAddress(address))
					{
						throw ApiErrorFactory.getInstance().createError(ApiErrorFactory.ERROR_INVALID_ADDRESS);
					}
					
					account = new Account(address);
				}
				 
			}
			catch(NullPointerException e)
			{
				//OPTION DOES NOT EXIST
			}
			catch(ClassCastException e)
			{
				//JSON EXCEPTION
				throw ApiErrorFactory.getInstance().createError(ApiErrorFactory.ERROR_JSON);
			}
			
			//SCAN
			Pair<Block, List<Transaction>> result = Controller.getInstance().scanTransactions(block, blockLimit, transactionLimit, type, service, account);
			
			//CONVERT RESULT TO JSON
			JSONObject json = new JSONObject();
			
			json.put("lastscanned", Base58.encode(result.getA().getSignature()));
			
			
			if(block != null)
			{
				json.put("amount", result.getA().getHeight() - block.getHeight() + 1);
			}
			else
			{
				json.put("amount", result.getA().getHeight());
			}
			
			JSONArray transactions = new JSONArray();
			for(Transaction transaction: result.getB())
			{
				transactions.add(transaction.toJson());
			}
			json.put("transactions", transactions);
			
			//RETURN
			return json.toJSONString();
		}
		catch(NullPointerException | ClassCastException e)
		{
			//JSON EXCEPTION
			LOGGER.info(e);
			throw ApiErrorFactory.getInstance().createError(ApiErrorFactory.ERROR_JSON);
		}
	}
	
	@SuppressWarnings("unchecked")
	@GET
	@Path("recipient/{address}/limit/{limit}")
	public String getTransactionsByRecipient(@PathParam("address") String address, @PathParam("limit") int limit)
	{
		JSONArray array = new JSONArray();
		List<Transaction> txs = DBSet.getInstance().getTransactionFinalMap().getTransactionsByRecipient(address, limit);
		for(Transaction transaction: txs)
		{
			array.add(transaction.toJson());
		}
		
		return array.toJSONString();
	}
	
	@SuppressWarnings("unchecked")
	@POST
	@Path("find")
	public String getTransactionsFind(String x)
	{
		JSONObject jsonObject = null;
		try
		{
			jsonObject = (JSONObject) JSONValue.parse(x);
		} catch (Exception e) {
			throw ApiErrorFactory.getInstance().createError(
				ApiErrorFactory.ERROR_JSON);
		}

		String address = (String) jsonObject.get("address");
		
		// CHECK IF VALID ADDRESS
		if (address != null && !Crypto.getInstance().isValidAddress(address)) {
			throw ApiErrorFactory.getInstance().createError(
					ApiErrorFactory.ERROR_INVALID_ADDRESS);
		}
		
		String sender = (String) jsonObject.get("sender");
		
		// CHECK IF VALID ADDRESS
		if (sender != null && !Crypto.getInstance().isValidAddress(sender)) {
			throw ApiErrorFactory.getInstance().createError(
					ApiErrorFactory.ERROR_INVALID_ADDRESS);
		}
		
		String recipient = (String) jsonObject.get("recipient");
		
		// CHECK IF VALID ADDRESS
		if (recipient != null && !Crypto.getInstance().isValidAddress(recipient)) {
			throw ApiErrorFactory.getInstance().createError(
					ApiErrorFactory.ERROR_INVALID_ADDRESS);
		}
		
		boolean count = false;
		if (jsonObject.containsKey("count")) {
			try
			{
				count = (boolean) jsonObject.get("count");
			} catch (Exception e) {
				throw ApiErrorFactory.getInstance().createError(
					ApiErrorFactory.ERROR_JSON);
			}
		}
		
		boolean desc = false;
		if (jsonObject.containsKey("desc")) {
			try
			{
				desc = (boolean) jsonObject.get("desc");
			} catch (Exception e) {
				throw ApiErrorFactory.getInstance().createError(
					ApiErrorFactory.ERROR_JSON);
			}
		}

		int offset = 0;
		if (jsonObject.containsKey("offset")) {
			try
			{
				offset = ((Long) jsonObject.get("offset")).intValue();
			} catch (Exception e) {
				throw ApiErrorFactory.getInstance().createError(
					ApiErrorFactory.ERROR_JSON);
			}
		}
		
		int limit = 0;
		if (jsonObject.containsKey("limit")) {
			try
			{
				limit = ((Long) jsonObject.get("limit")).intValue();
			} catch (Exception e) {
				throw ApiErrorFactory.getInstance().createError(
					ApiErrorFactory.ERROR_JSON);
			}
		}
		
		int minHeight = 0;
		if (jsonObject.containsKey("minHeight")) {
			try
			{
				minHeight = ((Long) jsonObject.get("minHeight")).intValue();
			} catch (Exception e) {
				throw ApiErrorFactory.getInstance().createError(
					ApiErrorFactory.ERROR_JSON);
			}
		}
		
		int maxHeight = 0;
		if (jsonObject.containsKey("maxHeight")) {
			try
			{
				maxHeight = ((Long) jsonObject.get("maxHeight")).intValue();
			} catch (Exception e) {
				throw ApiErrorFactory.getInstance().createError(
					ApiErrorFactory.ERROR_JSON);
			}
		}

		int type = 0;
		if (jsonObject.containsKey("type")) {
			try
			{
				type = ((Long) jsonObject.get("type")).intValue();
			} catch (Exception e) {
				throw ApiErrorFactory.getInstance().createError(
					ApiErrorFactory.ERROR_JSON);
			}
		}
		
		int service = -1;
		if ((type == Transaction.ARBITRARY_TRANSACTION || type == 0)
				&& jsonObject.containsKey("service")) {
			try
			{
				service = ((Long) jsonObject.get("service")).intValue();
			} catch (Exception e) {
				throw ApiErrorFactory.getInstance().createError(
					ApiErrorFactory.ERROR_JSON);
			}
			
			type = Transaction.ARBITRARY_TRANSACTION;
		}
		
		if (count) {
			return String.valueOf(DBSet.getInstance().getTransactionFinalMap().findTransactionsCount(address, sender, recipient, minHeight, maxHeight, type, service, desc, offset, limit));
		}
		
		JSONArray array = new JSONArray();
		List<Transaction> txs = DBSet.getInstance().getTransactionFinalMap().findTransactions(address, sender, recipient, minHeight, maxHeight, type, service, desc, offset, limit);
		for(Transaction transaction: txs)
		{
			array.add(transaction.toJson());
		}
		
		return array.toJSONString();
	}
	
	@SuppressWarnings("unchecked")
	@GET
	@Path("sender/{address}/limit/{limit}")
	public String getTransactionsBySender(@PathParam("address") String address, @PathParam("limit") int limit)
	{
		
		JSONArray array = new JSONArray();
		List<Transaction> txs = DBSet.getInstance().getTransactionFinalMap().getTransactionsBySender(address, limit);
		for(Transaction transaction: txs)
		{
			array.add(transaction.toJson());
		}
		
		return array.toJSONString();
	}
	
	@SuppressWarnings("unchecked")
	@GET
	@Path("address/{address}/type/{type}/limit/{limit}")
	public String getTransactionsByTypeAndAddress(@PathParam("address") String address, @PathParam("type") int type, @PathParam("limit") int limit)
	{
		
		JSONArray array = new JSONArray();
		List<Transaction> txs = DBSet.getInstance().getTransactionFinalMap().getTransactionsByTypeAndAddress(address, type, limit);
		for(Transaction transaction: txs)
		{
			array.add(transaction.toJson());
		}
		
		return array.toJSONString();
	}
}
