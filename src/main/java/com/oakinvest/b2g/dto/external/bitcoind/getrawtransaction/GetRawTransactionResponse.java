package com.oakinvest.b2g.dto.external.bitcoind.getrawtransaction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.oakinvest.b2g.dto.external.bitcoind.util.BitcoindResponse;

import java.io.Serializable;

/**
 * getrawtransaction response.
 * Created by straumat on 30/08/16.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetRawTransactionResponse extends BitcoindResponse implements Serializable {

	/**
	 * Result.
	 */
	private GetRawTransactionResult result;

	/**
	 * Getter of result.
	 *
	 * @return result
	 */
	public final GetRawTransactionResult getResult() {
		return result;
	}

	/**
	 * Setter of result.
	 *
	 * @param newResult the result to set
	 */
	public final void setResult(final GetRawTransactionResult newResult) {
		result = newResult;
	}

}
