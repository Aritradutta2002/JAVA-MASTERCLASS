package chat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Per-conversation ordering, membership, idempotent send, and durable per-device inbox. */
public final class ChatSystem {
    public enum MessageState { SENT, EDITED, DELETED }
    public enum InboxState { PENDING, DELIVERED, READ }
    public record Member(String userId, String role) { }
    public record Message(String id, String conversationId, String senderId, String text, long sequence, MessageState state, Instant createdAt) { }
    public record InboxItem(String userId, String conversationId, String messageId, long sequence, InboxState state) { }
    public static final class Conversation {
        private final String id; private final Set<Member> members=new HashSet<>(); private final Map<String,Message> messages=new HashMap<>(); private final Map<String,Set<String>> clientMessageIds=new HashMap<>(); private long sequence;
        public Conversation(String id) { this.id=id; }
        public synchronized void addMember(Member member){members.add(member);}
        public synchronized boolean hasMember(String userId){return members.stream().anyMatch(m->m.userId().equals(userId));}
        public synchronized Message send(String clientMessageId,String senderId,String text,Instant at){Objects.requireNonNull(clientMessageId);Objects.requireNonNull(senderId);Objects.requireNonNull(text);if(!hasMember(senderId))throw new IllegalStateException("Sender is not a member");Set<String> ids=clientMessageIds.computeIfAbsent(senderId,k->new HashSet<>());if(!ids.add(clientMessageId))return messages.values().stream().filter(m->m.id().equals(senderId+":"+clientMessageId)).findFirst().orElseThrow();String id=senderId+":"+clientMessageId;Message message=new Message(id,this.id,senderId,text,++sequence,MessageState.SENT,at);messages.put(id,message);return message;}
        public synchronized List<Member> membersForDelivery(){return List.copyOf(members);}
        public synchronized List<Message> historyAfter(long sequence,int limit){if(limit<=0)throw new IllegalArgumentException();return messages.values().stream().filter(m->m.sequence()>sequence).sorted((a,b)->Long.compare(a.sequence(),b.sequence())).limit(limit).toList();}
    }
    public static final class ChatService {
        private final Map<String,Conversation> conversations=new HashMap<>(); private final Map<String,InboxItem> inbox=new HashMap<>();
        public synchronized Conversation conversation(String id){return conversations.computeIfAbsent(id,Conversation::new);}
        public synchronized void join(String conversationId,String userId){conversation(conversationId).addMember(new Member(userId,"MEMBER"));}
        public synchronized Message send(String conversationId,String clientMessageId,String senderId,String text,Instant at){Conversation c=conversation(conversationId);Message m=c.send(clientMessageId,senderId,text,at);for(Member member:c.membersForDelivery()){String key=member.userId()+":"+m.id();inbox.putIfAbsent(key,new InboxItem(member.userId(),conversationId,m.id(),m.sequence(),InboxState.PENDING));}return m;}
        public synchronized int inboxSize(){return inbox.size();}
        public synchronized void deliver(String userId,String messageId){String key=userId+":"+messageId;InboxItem item=inbox.get(key);if(item==null)throw new IllegalStateException("No inbox item");inbox.put(key,new InboxItem(item.userId(),item.conversationId(),item.messageId(),item.sequence(),InboxState.DELIVERED));}
    }
    public static void main(String[] args){ChatService service=new ChatService();service.join("c1","a");service.join("c1","b");Instant now=Instant.parse("2026-01-01T00:00:00Z");Message first=service.send("c1","m1","a","hello",now);Message duplicate=service.send("c1","m1","a","hello",now);System.out.println("same="+first.id().equals(duplicate.id())+",sequence="+first.sequence()+",pending="+service.inboxSize());}
}
