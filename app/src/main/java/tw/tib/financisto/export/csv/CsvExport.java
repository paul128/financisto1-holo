/*******************************************************************************
 * Copyright (c) 2010 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 *
 * Contributors:
 *     Denis Solonenko - initial API and implementation
 ******************************************************************************/
package tw.tib.financisto.export.csv;

import android.content.Context;
import android.database.Cursor;

import tw.tib.financisto.datetime.DateUtils;
import tw.tib.financisto.db.DatabaseHelper;
import tw.tib.financisto.export.Export;
import tw.tib.financisto.db.DatabaseAdapter;
import tw.tib.financisto.model.*;
import tw.tib.financisto.model.Category;
import tw.tib.financisto.model.MyLocation;
import tw.tib.financisto.model.Payee;
import tw.tib.financisto.model.Project;
import tw.tib.financisto.utils.CurrencyCache;
import tw.tib.financisto.utils.Utils;
import tw.tib.financisto.model.Account;
import tw.tib.financisto.model.Currency;
import tw.tib.financisto.model.Transaction;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class CsvExport extends Export {

    static final String[] HEADER = "txid,date,time,status,account,amount,balance,currency,original amount,original currency,category,parent,payee,location,project,note".split(",");
    static final String TXID_HEADER = "txid";
    static final String STATUS_HEADER = "status";
    static final String BALANCE_HEADER = "balance";

    private static final MyLocation TRANSFER_IN = new MyLocation();
    private static final MyLocation TRANSFER_OUT = new MyLocation();

    static {
        TRANSFER_IN.title = "Transfer In";
        TRANSFER_OUT.title = "Transfer Out";
    }

    private final DatabaseAdapter db;
    private final CsvExportOptions options;

    private Map<Long, Category> categoriesMap;
    private Map<Long, Account> accountsMap;
    private Map<Long, Payee> payeeMap;
    private Map<Long, Project> projectMap;
    private Map<Long, MyLocation> locationMap;
    private Map<Long, Attribute> attributeMap;
    private Set<Long> usedAttributes;

    public CsvExport(Context context, DatabaseAdapter db, CsvExportOptions options) {
        super(context, false);
        this.db = db;
        this.options = options;
    }

    @Override
    protected String getExtension() {
        return ".csv";
    }

    @Override
    protected void writeHeader(BufferedWriter bw) throws IOException {
        if (options.writeUtfBom) {
            byte[] bom = new byte[3];
            bom[0] = (byte) 0xEF;
            bom[1] = (byte) 0xBB;
            bom[2] = (byte) 0xBF;
            bw.write(new String(bom, "UTF-8"));
        }
        if (options.includeHeader) {
            Csv.Writer w = new Csv.Writer(bw).delimiter(options.fieldSeparator);
            for (String h : HEADER) {
                if (h.equals(TXID_HEADER) && !options.exportTxIDs) continue;
                if (h.equals(STATUS_HEADER) && !options.includeTxStatus) continue;
                if (h.equals(BALANCE_HEADER) && !options.exportRunningBalance) continue;
                w.value(h);
            }
            if (options.exportAttributes) {
                attributeMap = db.getAllAttributesByIdMap();
                // only export attributes actually used by exporting transactions
                usedAttributes = new LinkedHashSet<>();
                try (Cursor c = db.getBlotterWithSplits(options.filter)) {
                    while (c.moveToNext()) {
                        long id = c.getLong(DatabaseHelper.BlotterColumns._id.ordinal());
                        Map<Long, String> attrs = db.getAllAttributesForTransaction(id);
                        usedAttributes.addAll(attrs.keySet());
                    }
                }
                for (long i : usedAttributes) {
                    w.value("attr:" + attributeMap.get(i).title);
                }
            }
            w.newLine();
        }
    }

    @Override
    protected void writeBody(BufferedWriter bw) throws IOException {
        Csv.Writer w = new Csv.Writer(bw).delimiter(options.fieldSeparator);
        try {
            accountsMap = db.getAllAccountsMap();
            categoriesMap = db.getAllCategoriesMap();
            payeeMap = db.getAllPayeeByIdMap();
            projectMap = db.getAllProjectsByIdMap(false);
            locationMap = db.getAllLocationsByIdMap(false);
            try (Cursor c = db.getBlotterWithSplits(options.filter)) {
                while (c.moveToNext()) {
                    Transaction t = Transaction.fromBlotterCursor(c);
                    writeLine(w, t);
                }
            }
        } finally {
            w.close();
        }
    }

    private void writeLine(Csv.Writer w, Transaction t) {
        Date dt = t.dateTime > 0 ? new Date(t.dateTime) : null;
        Category category = getCategoryById(t.categoryId);
        Project project = getProjectById(t.projectId);
        Account fromAccount = getAccount(t.fromAccountId);
        if (t.isTransfer()) {
            Account toAccount = getAccount(t.toAccountId);
            writeLine(w, t.id, dt, t.status, fromAccount.title, t.fromAmount, t.fromAccountBalance, fromAccount.currency.id, 0, 0, category, null, TRANSFER_OUT, project, t.note);
            writeLine(w, t.id, dt, t.status, toAccount.title, t.toAmount, t.toAccountBalance, toAccount.currency.id, 0, 0, category, null, TRANSFER_IN, project, t.note);
        } else {
            boolean isSplit = (category != null && category.isSplit());
            MyLocation location = getLocationById(t.locationId);
            Payee payee = getPayee(t.payeeId);
            if ((t.parentId == 0 && (!isSplit || options.exportSplitParents)) ||
                (t.parentId != 0 && options.exportSplits))
            {
                writeLine(w, t.id, dt, t.status, fromAccount.title, t.fromAmount, t.fromAccountBalance, fromAccount.currency.id, t.originalFromAmount, t.originalCurrencyId,
                        category, payee, location, project, t.note);
            }
        }
    }

    private void writeLine(Csv.Writer w, long transactionId, Date dt, TransactionStatus status, String account,
                           long amount, long balance, long currencyId,
                           long originalAmount, long originalCurrencyId,
                           Category category, Payee payee, MyLocation location, Project project, String note) {
        if (options.exportTxIDs) {
            w.value(String.valueOf(transactionId));
        }
        if (dt != null) {
            w.value(DateUtils.FORMAT_DATE_ISO_8601.format(dt));
            w.value(DateUtils.FORMAT_TIME_ISO_8601.format(dt));
        } else {
            w.value("~");
            w.value("");
        }
        if (options.includeTxStatus) {
            w.value(status.toString());
        }
        w.value(account);
        String amountFormatted = options.amountFormat.format(new BigDecimal(amount).divide(Utils.HUNDRED));
        w.value(amountFormatted);
        if (options.exportRunningBalance) {
            String balanceFormatted = options.amountFormat.format(new BigDecimal(balance).divide(Utils.HUNDRED));
            w.value(balanceFormatted);
        }
        Currency c = CurrencyCache.getCurrency(db, currencyId);
        w.value(c.name);
        if (originalCurrencyId > 0) {
            w.value(options.amountFormat.format(new BigDecimal(originalAmount).divide(Utils.HUNDRED)));
            Currency originalCurrency = CurrencyCache.getCurrency(db, originalCurrencyId);
            w.value(originalCurrency.name);
        } else {
            w.value("");
            w.value("");
        }
        w.value(category != null ? category.title : "");
        String sParent = buildPath(category);
        w.value(sParent);
        w.value(payee != null ? payee.title : "");
        w.value(location != null ? location.title : "");
        w.value(project != null ? project.title : "");
        w.value(note);
        if (options.exportAttributes) {
            Map<Long, String> attrs = db.getAllAttributesForTransaction(transactionId);
            for (long i : usedAttributes) {
                String value = attrs.get(i);
                if (value != null) {
                    w.value(value);
                }
                else {
                    w.value("");
                }
            }
        }
        w.newLine();
    }

    private String buildPath(Category category) {
        if (category == null || category.parent == null) {
            return "";
        } else {
            StringBuilder sb = new StringBuilder(category.parent.title);
            for (Category cat = category.parent.parent; cat != null; cat = cat.parent) {
                sb.insert(0, ":").insert(0, cat.title);
            }
            return sb.toString();
        }
    }

    @Override
    protected void writeFooter(BufferedWriter bw) throws IOException {
    }

    private Account getAccount(long accountId) {
        return accountsMap.get(accountId);
    }

    public Category getCategoryById(long id) {
        Category category = categoriesMap.get(id);
        if (category.id == 0) return null;
        if (category.isSplit()) {
            category.title = "SPLIT";
        }
        return category;
    }

    private Payee getPayee(long payeeId) {
        return payeeMap.get(payeeId);
    }

    private Project getProjectById(long projectId) {
        return projectMap.get(projectId);
    }

    private MyLocation getLocationById(long locationId) {
        return locationMap.get(locationId);
    }

}
