package com.oakinvest.b2g.service;

import com.oakinvest.b2g.domain.bitcoin.BitcoinAddress;
import com.oakinvest.b2g.domain.bitcoin.BitcoinBlock;
import com.oakinvest.b2g.domain.bitcoin.BitcoinTransaction;
import com.oakinvest.b2g.domain.bitcoin.BitcoinTransactionInput;
import com.oakinvest.b2g.domain.bitcoin.BitcoinTransactionOutput;
import com.oakinvest.b2g.dto.external.bitcoind.getblock.GetBlockResponse;
import com.oakinvest.b2g.dto.external.bitcoind.getblockhash.GetBlockHashResponse;
import com.oakinvest.b2g.dto.external.bitcoind.getrawtransaction.GetRawTransactionResponse;
import com.oakinvest.b2g.dto.external.bitcoind.getrawtransaction.GetRawTransactionResult;
import com.oakinvest.b2g.repository.bitcoin.BitcoinAddressRepository;
import com.oakinvest.b2g.repository.bitcoin.BitcoinBlockRepository;
import com.oakinvest.b2g.repository.bitcoin.BitcoinTransactionRepository;
import com.oakinvest.b2g.service.bitcoin.BitcoindService;
import com.oakinvest.b2g.util.bitcoin.BitcoindToDomainMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;


/**
 * Integrates blockchain data into the database.
 * Created by straumat on 04/09/16.
 */
@Service
public class IntegrationServiceImplementation implements IntegrationService {

	/**
	 * How many milli seconds in one second.
	 */
	public static final float MILLISECONDS_IN_SECONDS = 1000F;

	/**
	 * Transaction we will skip.
	 * 0e3e2357e806b6cdb1f70b54c3a3a17b6714ee1f0e68bebb44a74b1efd512098 -> Genesis block 1
	 * 4a5e1e4baab89f3a32518a88c31bc87f618f76673e2cc77ab2127b7afdeda33b -> Genesis block 2
	 * e411dbebd2f7d64dafeef9b14b5c59ec60c36779d43f850e5e347abee1e1a455 -> Block #71036 transaction to wrong address
	 * 2a0597e665ac3d1cabeede95cedf907934db7f639e477b3c77b242140d8cf728 -> Block #71036 transaction to wrong address
	 * a288fec5559c3f73fd3d93db8e8460562ebfe2fcf04a5114e8d0f2920a6270dc -> Block #71036 transaction to wrong address
	 */
	private static final String[]  TRANSACTIONS_BANNED = new String[] {
			"0e3e2357e806b6cdb1f70b54c3a3a17b6714ee1f0e68bebb44a74b1efd512098",
			"4a5e1e4baab89f3a32518a88c31bc87f618f76673e2cc77ab2127b7afdeda33b",
			"e411dbebd2f7d64dafeef9b14b5c59ec60c36779d43f850e5e347abee1e1a455",
			"2a0597e665ac3d1cabeede95cedf907934db7f639e477b3c77b242140d8cf728",
			"a288fec5559c3f73fd3d93db8e8460562ebfe2fcf04a5114e8d0f2920a6270dc"
	};

	/**
	 * Set containing transaction to skip.
	 */
	private static final java.util.Set<String> TRANSACTIONS_BANNED_SET = new HashSet<String>(Arrays.asList(TRANSACTIONS_BANNED));

	/**
	 * Logger.
	 */
	private final Logger log = LoggerFactory.getLogger(IntegrationService.class);

	/**
	 * Bitcoind service.
	 */
	@Autowired
	@Qualifier("BitcoindServiceImplementation") // FIXME Find a way to not set this in production.
	private BitcoindService bds;

	/**
	 * Status service.
	 */
	@Autowired
	private StatusService status;

	/**
	 * Bitcoin block repository.
	 */
	@Autowired
	private BitcoinBlockRepository bbr;

	/**
	 * Bitcoin transaction repository.
	 */
	@Autowired
	private BitcoinTransactionRepository btr;

	/**
	 * Bitcoin address repository.
	 */
	@Autowired
	private BitcoinAddressRepository bar;

	/**
	 * Mapper.
	 */
	@Autowired
	private BitcoindToDomainMapper mapper;

	/**
	 * Integrate a bitcoin block into the database.
	 *
	 * @param blockHeight block number
	 * @return true is integrated successfully
	 */
	@Override
	public final boolean integrateBitcoinBlock(final long blockHeight) {
		final long start = System.currentTimeMillis();
		log.info("Integrating bitcoin block number " + String.format("%09d", blockHeight));
		status.addLogMessage("Integrating bitcoin block number " + String.format("%09d", blockHeight));

		// Variables.
		boolean success = true;
		GetBlockHashResponse blockHash;
		GetBlockResponse block = new GetBlockResponse();
		ArrayList<GetRawTransactionResult> transactions = new ArrayList<>();
		ArrayList<String> addresses = new ArrayList<>();

		// Getting block hash & the block informations.
		blockHash = bds.getBlockHash(blockHeight);
		if (blockHash.getError() != null) {
			log.error("Error in calling getBlockHash " + blockHash.getError());
			status.addErrorMessage("Error in calling getBlockHash " + blockHash.getError());
			success = false;
		} else {
			// Getting block informations.
			block = bds.getBlock(blockHash.getResult());
			if (block.getError() != null) {
				log.error("Error in calling getBlock " + block.getError());
				status.addErrorMessage("Error in calling getBlock " + block.getError());
				success = false;
			}
		}

		// If the block is already in the database, we stop.
		if (bbr.findByHash(blockHash.getResult()) != null) {
			log.error("Block " + blockHeight + " already registred");
			status.addErrorMessage("Error in calling getBlock " + block.getError());
			success = false;
		}

		// Getting all transactions.
		if (success) {
			Iterator<String> it = block.getResult().getTx().iterator();
			while (it.hasNext() && success) {
				String transactionHash = it.next();
				// We don't treat the genesis block transaction and some other transactions that could not be well parsed
				if (!TRANSACTIONS_BANNED_SET.contains(transactionHash)) {
					GetRawTransactionResponse transaction = bds.getRawTransaction(transactionHash);

					if (transaction.getError() == null) {
						transactions.add(transaction.getResult());
					} else {
						log.error("Error in calling getRawTransaction " + transaction.getError());
						status.addErrorMessage("Error in calling getRawTransaction " + transaction.getError());
						success = false;
					}
				}
			}
		}

		// Getting all bitcoin addresses.
		if (success) {
			Iterator<GetRawTransactionResult> it = transactions.iterator();
			while (it.hasNext()) {
				it.next().getVout().forEach(s -> s.getScriptPubKey().getAddresses().forEach(a -> addresses.add(a)));
			}
		}

		// Persisting data.
		if (success) {
			Map bitcoinAddresses = new HashMap<String, BitcoinAddress>();

			// Saving the bitcoin addresses.
			Iterator<String> itAddresses = addresses.iterator();
			while (itAddresses.hasNext()) {
				String address = itAddresses.next();
				BitcoinAddress bAddress = bar.findByAddress(address);
				if (bAddress == null) {
					bAddress = bar.save(new BitcoinAddress(address));
					log.info("Address " + address + " created");
					status.addLogMessage("Address " + address + " created");
				} else {
					log.info("Address " + address + " already exists");
				}
				bitcoinAddresses.put(address, bAddress);
			}

			// Saving the block.
			BitcoinBlock b = mapper.blockResultToBitcoinBlock(block.getResult());
			bbr.save(b);
			log.info("Block " + b.getHash() + " created");
			status.addLogMessage("Block " + b.getHash() + " created");

			// Saving the transactions
			Iterator<GetRawTransactionResult> itTransactions = transactions.iterator();
			while (itTransactions.hasNext()) {
				GetRawTransactionResult t = itTransactions.next();
				BitcoinTransaction bt = mapper.rawTransactionResultToBitcoinTransaction(t);

				// Registering the block.
				bt.setBlock(b);

				// For each vout.
				Iterator<BitcoinTransactionOutput> outputsIterator = bt.getOutputs().iterator();
				while (outputsIterator.hasNext()) {
					BitcoinTransactionOutput o = outputsIterator.next();
					o.setTransaction(bt);
					o.getAddresses().forEach(a -> o.getBitcoinAddresses().add((BitcoinAddress) bitcoinAddresses.get(a)));
					o.getAddresses().forEach(a -> ((BitcoinAddress) bitcoinAddresses.get(a)).getDeposits().add(o));
				}

				// For each vin.
				Iterator<BitcoinTransactionInput> inputsIterator = bt.getInputs().iterator();
				while (inputsIterator.hasNext()) {
					BitcoinTransactionInput i = inputsIterator.next();
					i.setTransaction(bt);

					if (i.getTxId() != null) {
						// We retrieve the original transaction.
						BitcoinTransactionOutput originTransactionOutput = btr.findByTxId(i.getTxId()).getOutputByIndex(i.getvOut());
						i.setTransactionOutput(originTransactionOutput);

						// We set the addresses "from" if it's not a coinbase transaction.
						if (i.getCoinbase() == null) {
							// We retrieve all the addresses used in the transaction.
							originTransactionOutput.getAddresses().forEach(a -> bitcoinAddresses.put(a, bar.findByAddress(a)));
							// We add the input.
							originTransactionOutput.getAddresses().forEach(a -> ((BitcoinAddress) bitcoinAddresses.get(a)).getWithdrawals().add(i));
						}
					}
				}

				// Save the transaction.
				btr.save(bt);
				log.info("Transaction " + t.getTxid() + " created");
				status.addLogMessage("Transaction " + t.getTxid() + " created");
			}

		}
		final long elapsedTime = System.currentTimeMillis() - start;
		log.info("Integration of bitcoin block number " + String.format("%09d", blockHeight) + " done in " + elapsedTime / MILLISECONDS_IN_SECONDS + " secs");
		status.addLogMessage("Integration of bitcoin block number " + String.format("%09d", blockHeight) + " done in " + elapsedTime / MILLISECONDS_IN_SECONDS + " secs");
		return success;
	}

}
