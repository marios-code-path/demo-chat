# Chat Events Services

## Topic Manager


When streams are crossed, we should keep track of streams in a enumerated fashion (by UUID). Then when one stream crosses another, we 
can note that with a Map<STREAMID, Map<STREAMID, CONSUMER>> wherease the first STREAMID represents a UUID holding the corresponding Map
for it's consuming streams (down-streams). Thus the map represents a Map of consuming streams for every stream known (by it's UUID).
ALLCONSUMERS[STREAMID][CONSUMING_STREAMID]=CONSUMER.

We also want to reference the backing stream And thus a backing stream for any given STREAMID may be accessed as ALLSTREAMS[STREAMID] = BACKING_STREAM.

Furthermore, a Flux Processor can be retrieved for each STREAMID by ALLPROCESSORS[STREAMID] = PROCESSOR.


## ChatTopicService

This object is specific to users as members who are subscribed to arbitrary topics. A member's own topic should contain the output of all it's subscribed topics. Whie a member may write to any arbitrary topics, downstream members subscribed to a topic may receive it's messages.


