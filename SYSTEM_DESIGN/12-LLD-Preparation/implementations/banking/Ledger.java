package banking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Balanced double-entry ledger with idempotent transfers and immutable postings. */
public final class Ledger {
    public record Posting(String id,String accountId,BigDecimal amount,String currency,String transferId,Instant at){}
    public record TransferResult(String transferId,boolean accepted,String reason){}
    private final Map<String,List<Posting>> postingsByTransfer=new ConcurrentHashMap<>(); private final Map<String,BigDecimal> balances=new ConcurrentHashMap<>();
    public synchronized void openAccount(String accountId, BigDecimal openingBalance) { Objects.requireNonNull(accountId); Objects.requireNonNull(openingBalance); if (openingBalance.signum() < 0) throw new IllegalArgumentException("Opening balance cannot be negative"); balances.put(accountId, openingBalance); }
    public synchronized TransferResult transfer(String transferId,String debitAccount,String creditAccount,BigDecimal amount,String currency,Instant at){Objects.requireNonNull(transferId);Objects.requireNonNull(debitAccount);Objects.requireNonNull(creditAccount);Objects.requireNonNull(amount);Objects.requireNonNull(currency);if(amount.signum()<=0)return new TransferResult(transferId,false,"Amount must be positive");if(debitAccount.equals(creditAccount))return new TransferResult(transferId,false,"Accounts must differ");if(postingsByTransfer.containsKey(transferId))return new TransferResult(transferId,true,"Duplicate accepted");BigDecimal debit=balances.getOrDefault(debitAccount,BigDecimal.ZERO);if(debit.compareTo(amount)<0)return new TransferResult(transferId,false,"Insufficient funds");balances.put(debitAccount,debit.subtract(amount));balances.put(creditAccount,balances.getOrDefault(creditAccount,BigDecimal.ZERO).add(amount));postingsByTransfer.put(transferId,List.of(new Posting(transferId+":debit",debitAccount,amount.negate(),currency,transferId,at),new Posting(transferId+":credit",creditAccount,amount,currency,transferId,at)));return new TransferResult(transferId,true,"Accepted");}
    public synchronized BigDecimal balance(String accountId){return balances.getOrDefault(accountId,BigDecimal.ZERO);}
    public synchronized List<Posting> postings(String transferId){return postingsByTransfer.getOrDefault(transferId,List.of());}
    public synchronized boolean balanced(String transferId){List<Posting> p=postings(transferId);return p.size()==2&&p.stream().map(Posting::amount).reduce(BigDecimal.ZERO,BigDecimal::add).signum()==0;}
    public static void main(String[] args){Ledger ledger=new Ledger();ledger.openAccount("a",new BigDecimal("100.00"));TransferResult result=ledger.transfer("t1","a","b",new BigDecimal("25.00"),"USD",Instant.parse("2026-01-01T00:00:00Z"));TransferResult duplicate=ledger.transfer("t1","a","b",new BigDecimal("25.00"),"USD",Instant.now());System.out.println("accepted="+result.accepted()+",duplicate="+duplicate.accepted()+",balanced="+ledger.balanced("t1")+",a="+ledger.balance("a")+",b="+ledger.balance("b"));}
}
