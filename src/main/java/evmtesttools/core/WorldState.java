package evmtesttools.core;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import evmtesttools.util.Hex;

public class WorldState {
	private final Map<BigInteger, Account> state;

	public WorldState(Map<BigInteger, Account> state) {
		this.state = state;
	}

	/**
	 * Get the account associated with an address (or <code>null</code> if no such
	 * address exists).
	 *
	 * @param address
	 * @return
	 */
	public Account get(BigInteger address) {
		return state.get(address);
	}

	@Override
	public boolean equals(Object o) {
		if(o instanceof WorldState) {
			WorldState w = (WorldState) o;
			return state.equals(w.state);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return state.hashCode();
	}


	public JSONObject toJSON() throws JSONException {
		// Convert world state
		JSONObject json = new JSONObject();
		for (Map.Entry<BigInteger, Account> e : state.entrySet()) {
			json.put(Hex.toHexString(e.getKey()), e.getValue().toJSON());
		}
		return json;
	}

	public static WorldState fromJSON(JSONObject json) throws JSONException {
		Map<BigInteger, Account> st = new HashMap<>();
		for (String name : JSONObject.getNames(json)) {
			Account a = Account.fromJSON(json.getJSONObject(name));
			st.put(Hex.toBigInt(name), a);
		}
		return new WorldState(st);
	}
}
