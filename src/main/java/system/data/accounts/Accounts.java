package system.data.accounts;

import grabber.GrabberUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Accounts {
    private final static String accountsFile = GrabberUtils.getCurrentPath() + "/accounts.json";
    private static Accounts accounts;
    private List<Account> accountList = new ArrayList<>();

    private Accounts() { }

    public static Accounts getInstance() {
        if(accounts == null) {
            accounts = new Accounts();
            accounts.load();
        }
        return accounts;
    }

    /**
     * Reads accounts file(JSON) and creates Accounts object.
     */
    private void load() {
        try (BufferedReader reader = new BufferedReader(new FileReader(accountsFile))) {
            JSONArray accounts = (JSONArray) new JSONParser().parse(reader);
            for (Object loadedAccount: accounts) {
                accountList.add(new Account((JSONObject) loadedAccount));
            }
        } catch (IOException e) {
            GrabberUtils.err("No accounts file found.");
        } catch (ParseException e) {
            GrabberUtils.err("Could not parse accounts file.", e);
        }
    }

    /**
     * Saves accounts as JSON file.
     */
    public void save() {
        try(BufferedWriter writer = new BufferedWriter(new FileWriter(accountsFile))) {
            JSONArray accountArray = new JSONArray();
            for(Account account: accountList) {
                accountArray.add(account.getAsJSONObject());
            }
            writer.write(accountArray.toJSONString());
        } catch(IOException e) {
            GrabberUtils.err(e.getMessage(), e);
        }
    }

    public void addAccount(Account newAccount) {
        if(!accountList.contains(newAccount)) {
            accountList.add(newAccount);
        } else {
            getAccount(newAccount.getDomain()).setCookies(newAccount.getCookies());
        }
        save();
    }

    /**
     * Get account for domain if it exists.
     * @param domain Account domain
     * @return Found account or empty account
     */
    public Account getAccount(String domain) {
        for(Account account: accountList) {
            if(account.getDomain().equals(domain)) return account;
        }
        return new Account(domain, new HashMap<>());
    }
}
