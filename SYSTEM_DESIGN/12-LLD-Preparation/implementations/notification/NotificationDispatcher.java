package notification;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Preference-aware notification lifecycle, bounded retry policy, and idempotent provider calls. */
public final class NotificationDispatcher {
    public enum Channel { EMAIL, SMS, PUSH }
    public enum NotificationState { PENDING, SENT, FAILED, SUPPRESSED }
    public enum AttemptState { STARTED, DELIVERED, TRANSIENT_FAILURE, PERMANENT_FAILURE }
    public record Preference(Channel channel, boolean enabled, boolean quietHours) { }
    public interface Provider { AttemptState send(String idempotencyKey, Channel channel, String recipient, String body); }
    public record Notification(String id, String recipient, Channel channel, String body, NotificationState state, int attempts) { }
    public record Attempt(String notificationId, int number, AttemptState state, Instant at) { }
    private final Map<String,Notification> notifications=new HashMap<>(); private final Map<String,Preference> preferences=new HashMap<>(); private final Deque<String> pending=new ArrayDeque<>(); private final Provider provider; private final int maxAttempts;
    public NotificationDispatcher(Provider provider,int maxAttempts){this.provider=Objects.requireNonNull(provider);if(maxAttempts<1)throw new IllegalArgumentException();this.maxAttempts=maxAttempts;}
    public void setPreference(String recipient,Channel channel,Preference preference){preferences.put(recipient+":"+channel,preference);}
    public Notification submit(String id,String recipient,Channel channel,String body,Instant now){if(notifications.containsKey(id))return notifications.get(id);Preference p=preferences.get(recipient+":"+channel);if(p==null||!p.enabled()||p.quietHours()){Notification n=new Notification(id,recipient,channel,body,NotificationState.SUPPRESSED,0);notifications.put(id,n);return n;}Notification n=new Notification(id,recipient,channel,body,NotificationState.PENDING,0);notifications.put(id,n);pending.addLast(id);return n;}
    public Notification dispatchOne(Instant now){String id=pending.pollFirst();if(id==null)throw new IllegalStateException("No pending notification");Notification current=notifications.get(id);if(current.state()!=NotificationState.PENDING)return current;int number=current.attempts()+1;AttemptState result=provider.send(id,current.channel(),current.recipient(),current.body());NotificationState next;if(result==AttemptState.DELIVERED)next=NotificationState.SENT;else if(result==AttemptState.PERMANENT_FAILURE||number>=maxAttempts)next=NotificationState.FAILED;else next=NotificationState.PENDING;Notification updated=new Notification(id,current.recipient(),current.channel(),current.body(),next,number);notifications.put(id,updated);if(next==NotificationState.PENDING)pending.addLast(id);return updated;}
    public Notification notification(String id){return notifications.get(id);}
    public int pendingSize(){return pending.size();}
    public static void main(String[] args){NotificationDispatcher d=new NotificationDispatcher((key,channel,to,body)->AttemptState.DELIVERED,3);d.setPreference("u1",Channel.EMAIL,new Preference(Channel.EMAIL,true,false));d.submit("n1","u1",Channel.EMAIL,"hello",Instant.now());Notification n=d.dispatchOne(Instant.now());System.out.println("state="+n.state()+",attempts="+n.attempts());}
}
