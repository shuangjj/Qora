package api;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import qora.account.Account;
import qora.account.PrivateKeyAccount;
import qora.assets.Asset;
import qora.crypto.Crypto;
import qora.naming.Name;
import qora.payment.Payment;
import qora.transaction.Transaction;
import utils.APIUtils;
import utils.GZIP;
import utils.Pair;
import utils.StorageUtils;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;

import controller.Controller;
import database.DBSet;

@Path("namestorage")
@Produces(MediaType.APPLICATION_JSON)
public class NameStorageResource {

	public static final String ASSET_JSON_KEY = "asset";
	public static final String AMOUNT_JSON_KEY = "amount";
	public static final String PAYMENTS_JSON_KEY = "payments";
	@Context
	HttpServletRequest request;

	@SuppressWarnings("unchecked")
	@GET
	@Path("/{name}/list")
	public String listNameStorage(@PathParam("name") String name) {

		Name nameObj = DBSet.getInstance().getNameMap().get(name);

		if (nameObj == null) {
			throw ApiErrorFactory.getInstance().createError(
					ApiErrorFactory.ERROR_NAME_NOT_REGISTERED);
		}

		Map<String, String> map = DBSet.getInstance().getNameStorageMap()
				.get(name);

		JSONObject json = new JSONObject();
		if (map != null) {
			Set<String> keySet = map.keySet();

			for (String key : keySet) {
				json.put(key, map.get(key));
			}
		}

		return json.toJSONString();
	}
	
	@SuppressWarnings("unchecked")
	@GET
	@Path("/{name}/keys")
	public String listKeysNameStorage(@PathParam("name") String name) {

		Name nameObj = DBSet.getInstance().getNameMap().get(name);

		if (nameObj == null) {
			throw ApiErrorFactory.getInstance().createError(
					ApiErrorFactory.ERROR_NAME_NOT_REGISTERED);
		}

		Map<String, String> map = DBSet.getInstance().getNameStorageMap()
				.get(name);

		JSONArray json = new JSONArray();
		if (map != null) {
			Set<String> keySet = map.keySet();

			for (String key : keySet) {
				json.add(key);
			}
		}

		return json.toJSONString();
	}

	@SuppressWarnings("unchecked")
	@GET
	@Path("/{name}/key/{key}")
	public String getNameStorageValue(@PathParam("name") String name,
			@PathParam("key") String key) {
		Name nameObj = DBSet.getInstance().getNameMap().get(name);

		if (nameObj == null) {
			throw ApiErrorFactory.getInstance().createError(
					ApiErrorFactory.ERROR_NAME_NOT_REGISTERED);
		}

		Map<String, String> map = DBSet.getInstance().getNameStorageMap()
				.get(name);

		JSONObject json = new JSONObject();
		if (map != null && map.containsKey(key)) {
			json.put(key, map.get(key));
		}

		return json.toJSONString();
	}

	@SuppressWarnings("unchecked")
	@POST
	@Path("/update/{name}")
	public String updateEntry(String x, @PathParam("name") String name) {
		try {
			APIUtils.disallowRemote(request);

			// READ JSON
			JSONObject jsonObject = (JSONObject) JSONValue.parse(x);

			// CHECK IF WALLET EXISTS
			if (!Controller.getInstance().doesWalletExists()) {
				throw ApiErrorFactory.getInstance().createError(
						ApiErrorFactory.ERROR_WALLET_NO_EXISTS);
			}

			// CHECK WALLET IN SYNC
			if (Controller.getInstance().getStatus() != Controller.STATUS_OK) {
				throw ApiErrorFactory.getInstance().createError(
						ApiErrorFactory.ERROR_WALLET_NOT_IN_SYNC);
			}

			Name nameObj = DBSet.getInstance().getNameMap().get(name);
//			Controller.getInstance().getAccountByAddress(name)

			String creator;
			if (nameObj == null) {
				
				//check if addressstorage
				Account accountByAddress = Controller.getInstance().getAccountByAddress(name);
				
				if(accountByAddress == null)
				{
					throw ApiErrorFactory.getInstance().createError(
							ApiErrorFactory.ERROR_NAME_NOT_REGISTERED);
				}

				creator = name;
				
			} else {
				creator = nameObj.getOwner().getAddress();
			}

			// CHECK ADDRESS
			if (!Crypto.getInstance().isValidAddress(creator)) {
				throw ApiErrorFactory.getInstance().createError(
						ApiErrorFactory.ERROR_INVALID_ADDRESS);
			}

			// CHECK ACCOUNT IN WALLET
			if (Controller.getInstance().getAccountByAddress(creator) == null) {
				throw ApiErrorFactory.getInstance().createError(
						ApiErrorFactory.ERROR_WALLET_ADDRESS_NO_EXISTS);
			}

			jsonObject.put("name", name);
			
			String paymentsOpt = (String) jsonObject.get(PAYMENTS_JSON_KEY);
			
			List<Payment> resultPayments = new ArrayList<>();
			if(paymentsOpt != null)
			{
				// READ JSON
				JSONObject paymentsJson = (JSONObject) JSONValue.parse(paymentsOpt);
				Set<String> addresses = paymentsJson.keySet();
				
				
				for (String address : addresses) {
					
					if (!Crypto.getInstance().isValidAddress(address)) {
						throw ApiErrorFactory.getInstance().createError(
								ApiErrorFactory.ERROR_INVALID_ADDRESS);
					}
					
					String amountAssetJson = (String) paymentsJson.get(address);
					
					JSONObject amountAssetJsonObject = (JSONObject) JSONValue.parse(amountAssetJson);
					
					String amount = (String) amountAssetJsonObject.get(AMOUNT_JSON_KEY);
					
					BigDecimal bdAmount;
					try 
					{
						bdAmount = new BigDecimal(amount);
						bdAmount = bdAmount.setScale(8);
					} catch (Exception e) {
						throw ApiErrorFactory.getInstance().createError(
							ApiErrorFactory.ERROR_INVALID_AMOUNT);
					}
					
					Asset paymentAsset = Controller.getInstance().getAsset(new Long(0L));
					
					if(amountAssetJsonObject.containsKey(ASSET_JSON_KEY)) {
						try {
							paymentAsset = Controller.getInstance().getAsset(new Long(amountAssetJsonObject.get(ASSET_JSON_KEY).toString()));
						} catch (Exception e) {
							throw ApiErrorFactory.getInstance().createError(
								ApiErrorFactory.ERROR_INVALID_ASSET_ID);
						}
					}
					
					Payment payment = new Payment(new Account(address), paymentAsset.getKey(), bdAmount);
					resultPayments.add(payment);
					
				}
				
				
				
//				remove payments from json
				jsonObject.remove(PAYMENTS_JSON_KEY);
				
			}
			
			List<Payment> paymentsForCalculation = new ArrayList<>(resultPayments);

			String jsonString = jsonObject.toJSONString();
			String compressedJsonString = GZIP.compress(jsonString);

			if (compressedJsonString.length() < jsonString.length()) {
				jsonString = compressedJsonString;
			}

			byte[] bytes = jsonString.getBytes(StandardCharsets.UTF_8);
			List<String> askApicalls = new ArrayList<String>();	
			List<String> decompressedValue = new ArrayList<String>();
			JSONObject jsonObjectForCheck = (JSONObject) JSONValue.parse(x);
			// TODO IN CASE OF MULTIPAYMENT 4000 CAN BE A PROBLEM, THE FIRST TX CAN CONTAIN MULTIPAYMENTS WHICH NEED EXTRA SPACE
			// IF VALUE TOO LARGE FOR ONE ARB TX AND WE ONLY HAVE ADDCOMPLETE
			// WITH ONE KEY
			if (bytes.length > 4000
					&& jsonObjectForCheck.containsKey(StorageUtils.ADD_COMPLETE_KEY)
					&& jsonObjectForCheck.keySet().size() == 1) {
				JSONObject innerJsonObject = (JSONObject) JSONValue.parse((String) jsonObjectForCheck.get(StorageUtils.ADD_COMPLETE_KEY));
				if (innerJsonObject.keySet().size() == 1) {
					// Starting Multi TX

					String key = (String) innerJsonObject.keySet().iterator()
							.next();
					String value = (String) innerJsonObject.get(key);

					Iterable<String> chunks = Splitter.fixedLength(3500).split(
							value);
					List<String> arbTxs = Lists.newArrayList(chunks);

					BigDecimal completeFee = BigDecimal.ZERO;
					List<Pair<byte[], BigDecimal>> allTxPairs = new ArrayList<>();

					boolean isFirst = true;
					for (String valueString : arbTxs) {
						Pair<String, String> keypair = new Pair<String, String>(
								key, valueString);
						JSONObject storageJsonObject;
						if (isFirst) {
							storageJsonObject = StorageUtils
									.getStorageJsonObject(
											Collections.singletonList(keypair),
											null, null, null, null, null);
							isFirst = false;
						} else {
							storageJsonObject = StorageUtils
									.getStorageJsonObject(null, null, null,
											null,
											Collections.singletonList(keypair), null);
						}
						storageJsonObject.put("name", name);

						String jsonStringForMultipleTx = storageJsonObject
								.toJSONString();

						String compressedjsonStringForMultipleTx = GZIP
								.compress(jsonStringForMultipleTx);

						if (compressedjsonStringForMultipleTx.length() < jsonStringForMultipleTx
								.length()) {
							jsonStringForMultipleTx = compressedjsonStringForMultipleTx;
						}

						byte[] resultbyteArray = jsonStringForMultipleTx
								.getBytes(StandardCharsets.UTF_8);
						BigDecimal currentFee = Controller
								.getInstance()
								.calcRecommendedFeeForArbitraryTransaction(
										resultbyteArray, paymentsForCalculation).getA();
						//multipayment only for first tx
						paymentsForCalculation = null;

						completeFee = completeFee.add(currentFee);

						allTxPairs.add(new Pair<>(resultbyteArray, currentFee));

						String decompressed = GZIP.webDecompress(jsonStringForMultipleTx);
						askApicalls.add("POST namestorage/update/" + name
								+ "\n"
								+ decompressed
								+ "\nfee: " + currentFee.toPlainString());
						decompressedValue.add(decompressed);
					}
					
					if(allTxPairs.size() > ApiErrorFactory.BATCH_TX_AMOUNT)
					{
						throw ApiErrorFactory.getInstance().createError(
								ApiErrorFactory.ERROR_TX_AMOUNT);
					}
					
					//recalculating qora amount
					BigDecimal newCompleteFee = BigDecimal.ZERO;
					BigDecimal oldAmount = BigDecimal.ZERO;
					List<Pair<byte[], BigDecimal>> newPairs = new ArrayList<Pair<byte[],BigDecimal>>();
					for (Pair<byte[], BigDecimal> pair : allTxPairs) {
						if(oldAmount.equals(BigDecimal.ZERO))
						{
							oldAmount = pair.getB();
							newCompleteFee = oldAmount;
							newPairs.add(pair);
							continue;
						}
						
						BigDecimal newAmount = oldAmount.multiply(new BigDecimal(1.15));
						newAmount = newAmount.setScale(0, BigDecimal.ROUND_UP).setScale(8); 
						pair.setB(newAmount);
						newPairs.add(pair);
						
						oldAmount = newAmount;
						
						newCompleteFee= newCompleteFee.add(newAmount);
					}
					
					String apicalls = "";
					for (int i = 0; i < newPairs.size(); i++) {
						apicalls +=	"POST namestorage/update/" + name
								+ "\n"
								+ decompressedValue.get(i)
								+ "\nfee: " + newPairs.get(i).getB().toPlainString()+"\n";
					}
					
					String basicInfo = getMultiPaymentsWarning(resultPayments);

					basicInfo = "Because of the size of the data this call will create "
							+ allTxPairs.size()
							+ " transactions.\nAll Arbitrary Transactions will cost: "
							+ newCompleteFee.toPlainString() + " Qora.\nDetails:\n\n";

//					basicInfo += StringUtils.join(askApicalls, "\n");
					basicInfo += apicalls;

					APIUtils.askAPICallAllowed(basicInfo, request);

					//CHECK WALLET UNLOCKED
					if (!Controller.getInstance().isWalletUnlocked()) {
						throw ApiErrorFactory.getInstance().createError(
								ApiErrorFactory.ERROR_WALLET_LOCKED);
					}
					
					// GET ACCOUNT
					PrivateKeyAccount account = Controller.getInstance()
							.getPrivateKeyAccountByAddress(creator);
					if (account == null) {
						throw ApiErrorFactory.getInstance().createError(
								ApiErrorFactory.ERROR_INVALID_ADDRESS);
					}
					
					if (account.getBalance(1, DBSet.getInstance()).compareTo(
							completeFee) == -1) {
						throw ApiErrorFactory.getInstance().createError(
								ApiErrorFactory.ERROR_NO_BALANCE);
					}
					
					Pair<Transaction, Integer> result;
					String results = "";
					for (Pair<byte[], BigDecimal> pair : newPairs) {
						result = Controller.getInstance()
								.createArbitraryTransaction(account, resultPayments.size() > 0 ? resultPayments : null, 10,
										pair.getA(), pair.getB());
						//add multipayments only to first tx
						resultPayments.clear();

						results += ArbitraryTransactionsResource
								.checkArbitraryTransaction(result) + "\n";
					}

					return results;

				}
			}
			
			
			
			String basicInfo = getMultiPaymentsWarning(resultPayments);
			
			
			BigDecimal fee = Controller.getInstance()
					.calcRecommendedFeeForArbitraryTransaction(bytes, resultPayments).getA();
			
			
			
			APIUtils.askAPICallAllowed(basicInfo +
					"POST namestorage/update/" + name + "\n"
							+ GZIP.webDecompress(jsonString) + "\nfee: "
							+ fee.toPlainString(), request);

			//CHECK WALLET UNLOCKED
			if (!Controller.getInstance().isWalletUnlocked()) {
				throw ApiErrorFactory.getInstance().createError(
						ApiErrorFactory.ERROR_WALLET_LOCKED);
			}
			
			// GET ACCOUNT
			PrivateKeyAccount account = Controller.getInstance()
					.getPrivateKeyAccountByAddress(creator);
			if (account == null) {
				throw ApiErrorFactory.getInstance().createError(
						ApiErrorFactory.ERROR_INVALID_ADDRESS);
			}
			
			// SEND PAYMENT
			Pair<Transaction, Integer> result = Controller.getInstance()
					.createArbitraryTransaction(account,resultPayments , 10, bytes, fee);

			return ArbitraryTransactionsResource
					.checkArbitraryTransaction(result);

		} catch (NullPointerException e) {
			// JSON EXCEPTION
			throw ApiErrorFactory.getInstance().createError(
					ApiErrorFactory.ERROR_JSON);
		} catch (ClassCastException e) {
			// JSON EXCEPTION
			throw ApiErrorFactory.getInstance().createError(
					ApiErrorFactory.ERROR_JSON);
		}

	}

	public String getMultiPaymentsWarning(List<Payment> resultPayments) {
		
		if(resultPayments.size() == 0)
		{
			return "";
		}
		String basicInfo = "WARNING : This call contains multipayments:\n";
		for (Payment payment : resultPayments) {
			basicInfo +=  "Recipient " + payment.getRecipient().getAddress() + " Amount: " + payment.getAmount().toPlainString() + " AssetID " + payment.getAsset() + (payment.getAsset() == 0L ? "(Qora)\n" : "\n");
		}
		basicInfo +="\n";
		return basicInfo;
	}

}
