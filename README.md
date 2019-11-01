Fibry
===

Fibry is an experimental Actor System built to be simple and flexible to use. Hopefully it will also be fun to use.
Fibry is the the **first Java Actor System using fibers from [Project Loom](https://openjdk.java.net/projects/loom/)**.

Project Loom is an OpenJDK project that is expected to bring fibers (green threads) and continuations (co-routines) to Java.
Fibry 1.X works with any version of Java **starting from Java 8**, while Fibry 2.X is targeting Java 11, but in both cases you will need to use Loom if you want to leverage the power of fibers.
Fibry aims to replicate some of the features of the Erlang Actor System in Java.
Fibry allows you to send code to be execute in the thread/fiber of an actor, a mechanism similar to the one used in Chromium.

Simplicity first
===
*Fibry* has been designed to be simple yet flexible:
- Your actor can and should use synchronous logic
- There is a series of Stereotypes to handle common scenarios
- You actors don't need to extend any particular class but they can just implement **Consumer** or **Function**
- You actors have anyway the option to extend **CustomActor** and **CustomActorWithResult**, if this suits you best 
- If you choose to simply implements **Consumer** and **Function**, your actors can also be used "transparently" in code that knows nothing about Fibry
- It is simple to retrieve the result of a message
- It is possible to send messages to named actors even before they are created, potentially simplifying your logic; the messages can be discarded or processed when the actor will be available 
- There is a fluid interface to build the actors
- You can receive messages of your choice while processing a message  
- Many types of actor implement the **Executor** interface, so you can "send code" to be executed in the thread/fiber of almost any actors, and use them on service that are not actor-aware
- **Fibry has no dependencies**, so no conflicts, no surprises and just a tiny jar available in the Maven Central repository
- Most actors can be converted to **Reactive Flow Subscribers** (TCK tested), calling *asReactiveSubscriber()*
- Fibry implements a very simple **Map/Reduce mechanism**, limited to the local computer.
- Fibry implements a very simple **Pub/Sub** mechanism, limited to the local computer.
- Fibry implements a simple **TCP port forwarding**, both as a Stereotype and as a small cli application: TcpForwarding
- Fibry implements some simple mechanisms to help processing messages in **batches**

Some numbers
===
So, fibers are better than threads. Got it. How much better?
Very much. Depending on your problem, you can consider them 10X-100X better than threads.
Please remember that Fibry is not optimized for performance, though performance have been taken into high consideration.
Also Loom is not completed yet, so its performance can change.
I took some informal benchmarks using a C5.2xlarge VM instance, without tuning of the OS or of Loom:

- Number of concurrent threads that can be created without OS tuning: around 3K
- Number of concurrent fibers that can be created: more than 3M (**300x better**)
- Threads created per second: 15K
- Fibers created per second: 600K (**40x better**)
- Sync messages per second, between 2 threads (requires thread switching): 50K
- Sync messages per second, between 2 threads (requires fiber switching): 150K (**3x better**)

As an indication, Fibry can send around 7-8M of messages per second from a single core, under low thread contention.

Including Fibry in your projects
===
You can find Fibry on Maven Central.

To include it using Gradle:
```gradle
compile group: 'eu.lucaventuri', name: 'fibry', version: '1.0.3'
```

To include it using Maven:
```xml
<dependency>
    <groupId>eu.lucaventuri</groupId>
    <artifactId>fibry</artifactId>
    <version>2.0.0</version>
</dependency>
```

Why fibers?
===
Fibers, or green threads, are lightweight threads. Lightweight means that you can have many of them, and in fact Fibry will be happy to keep running several million of fibers at the same time, if that's what you need.
With threads, depending on your configuration, you can maybe have some tens of thousands.

Surely you can use thread pools, but if you need to execute long operations this can be a problem, and in fact you might need to use asynchronous network operations to scale.
And **asynchronous code is hard**. It can be really hard. Even a simple logic can be split in several callbacks and create endless issues.
You can do amazing stuff with just a single thread, but you pay a price for it.

With fibers you can write your actors using synchronous calls. Yep, boring, plain, synchronous calls, and your project will still scale like crazy.
That's why **Fibry** was born: to let you write simple actors with synchronous logic.

Project Loom?
===
That's the trick. Project Loom enable fibers. While fibers are nice but themselves, they were not very useful to do network operations until JDK 13 (due in September 2019) merged [JEP 353](https://openjdk.java.net/jeps/353), that rewrote part the network stack of Java to be Fiber friendly.
Unfortunately, Loom is not yet merged into the OpenJDK, so you will have to build it by yourself. This might sounds scaring, but it is not.
On Linux, building Loom is a matter of running a few commands and waiting:
```bash
hg clone http://hg.openjdk.java.net/loom/loom 
cd loom 
hg update -r fibers
sh configure  
make images
```

Please consider that to compile Loom you need a "bootstrap JDK" that should be Java 12 or 13 (I guess 14 also works as Looms is already on JDK 14). I used Zulu 12 for my tests.
Most likely you will need to install some packages, but *sh configure* kindly tells you the command to run.
When you are done, you will have a new JVM at your disposal. Mine was on this path: **build/linux-x86_64-server-release/images/jdk/bin/java**

More info in [Loom Wiki](https://wiki.openjdk.java.net/display/loom/Main#Main-DownloadandBuildfromSource)
On Windows you might have to use a Virtual Machine, and I would recommend to avoid shared folders as they can be issues with symbolic links.

To recognize Loom you don't need to do anything particular, **Fibry will detect if fibers are available** and use them automatically. But you do have to choose to use the FIBER or AUTO strategy, as Fibry allows you to force the creation of threads, if that's what you need.

Creating actors with the ActorSystem class
===
While using actors is very simple, there are several ways to create the actors and to use them, so you will need to decide how you want your system to be built.

The most flexible way to create actors is using ActorSystem, a class implementing a fluid interface.
You might create anonymous and named actors, the difference being that named actors have a name and they can be used without having ac Actor object, and in fact you can send messages even before the actor has been created, which helps reducing race conditions.
You can choose the strategy: AUTO (the default, using fibers if available), FIBER (using fibers, throwing an exception if they are not available) and THREAD (using threads).
You can supply an initial state, which is mostly useful for thread confinement.

You can create several types of actor:
- Normal Actors: they receive messages without returning any result; they need to implement Consumer or BiConsumer (if you need access to the actor)
- Returning Actors: they compute a result and return a CompletableFuture for each message; they need to implement Function or BiFunction  (if you need access to the actor)
- Multi-messages actors: they can handle more than one type of message; they need a message handler with public methods in the form *onXXX(message)*, and they can return or not a value
- Receiving actors: they are normal actor that can also "receive", meaning that they can ask the actor system to deliver some particular message while processing another message, e.g. if you are waiting for another actor to provide some information; they need to implement BiConsumer
- Receiving and returning actors: the are receiving actors that can also return a result; they need to implement BiFunction

Please take into consideration that while Receiving actors are the most powerful, there is some overhead in their use, and the receive operation must be used carefully as in the worst case it might have to scan all the message in the queue. In fact, I expect many cases to be covered with returning actors (e.g. you ask something to another actor and wait for the result), and they should be preferred.

Let's see now how to create an actor:
```Java
Actor<Integer, Integer, Void> actor = ActorSystem.anonymous().newActorWithReturn(n -> n*n);
```

Using actors
===
Using actors is super simple. The main functions are **sendMessage()** and **sendMessageReturn()**. To get a result from the previous actor, we can do:
```java
actor.sendMessageReturn(2).get()
```

But actors also implement the **Consumer** and the **Function** interface, so the previous code can be rewritten like this:
```java
actor.apply(2).intValue()
```

Please notice that **apply()** is blocking and it is therefore equivalent to **sendMessageReturnWait()**, while sendMessageReturn() returns a CompletableFuture that can allow the code to do other things while waiting.
An excessive use of apply() and sendMessageReturnWait() can have negative effects on performance.

Thread confinement
===
Actors systems exist to implement thread confinement: your thread/fiber executes in the same thread/fiber and therefore you don't need synchronization or thread-safe classes.
Usually the logic of the actor is supplied during the creation, but sometimes instead of implementing several message types it would be easier to just "send some code" to be executed in the context of the actor.
An example would be **Platform.runLater()** in JavaFX.
Fibry support this behavior for every actor, with the methods *execAsync()*, *execAndWait()* and *execFuture()*, all accepting Runnable and Consumer interface.
In addition, almost every Actor implements the Executor interface.  
 
Creating actors with the Stereotypes class
===
As you start to us actors, some patterns might emerge on the way that the actors are configured.
Some fo this patterns have been implemented in the Stereotypes class.
Please check it and feel free to send me suggestions for new stereotypes.
You are encouraged to use the **Stereotypes** class instead of relying on ActorSystem, if it provides something useful to you.

Some examples:
- *workersAsConsumerCreator()*: creates a master actor returned as Consumer; every call to **accept()** will spawn a new actor that will process the message, making multi-thread as simple as it can be
- *workersAsFunctionCreator()*: as before, but it accepts a Function, so it can actually return a result
- *embeddedHttpServer*: creates and embedded HTTP Server (using the standard HTTP Server included in Java), that process any request with an actor
- *sink()*: creates an actor that cannot process messages, but that can still be used for thread confinement, sending code to it
- *runOnce()*: creates an actor that executes some logic in a separated thread, once.
- *schedule()*: creates an actor that executes some logic in a separated thread, as many times as requested, as often as requested
- *tcpAcceptor()*: creates a master actor that will receive TCP connections, delegating the processing of each connection to a dedicated fiber. This is nice for IoI, to design a chat system or in general if you have a proxy.

Please check the **examples** package for inspiration.

This is a very simple HTTP Hello World:
```java
Stereotypes.def().embeddedHttpServer(8080, new Stereotypes.HttpStringWorker("/", ex -> "Hello world!"));
```

Extending CustomActor and CustomActorWithResult
===
For maximum flexibility, sometimes you might want to just be an actor, instead of implementing some interface and struggle to customize its behavior.
It is possible to do so extending **CustomActor** or **CustomActorWithResult**, depending on the type of actor that you need. The only method required is **onMessage()**.
Just remember to call **CreationStrategy.start()** to start it.

Shutting down the actors
===
Shutting down the actors is a bit complicated, depending on which goal you want to achieve.
One way is to call **askExit()**, which will ask the actor to terminate as soon as possible, which by default means after finishing the current message; long running actors should check for their **isExiting()** method. This will however loose the messages on the queue (and the actor will clear the queue). 
Another way is to call **sendPoisonPill()**, which will queue a message able to shut down the actor: the messages after the poison pill will be lost, the ones before it will be processed.
The actors are Closeable(), so they can be put in a try-with-resources block. Please keep in mind that the default behavior is to call **askExit()**, so when the code leaves the try-with-resources block the actor might still be alive and working. This behavior can be customised using a different ClosingStrategy. For example, **SEND_POISON_PILL_AND_WAIT** will block in the try catch until all the messages in the queue (before the poison pill) are processed.
The ClosingStrategy can be set using the **strategy()** call in ActorSystem, which can also set creation strategy.
Using blocking try-with resources with more than one actor might be a bit complicated, an might not be worth it. If that's the chosen strategy, it might be better to have only one actor blocking on close, to avoid race conditions.

For more information, please look at the Exitable class.

Named Actors
===
Named actors can allow clients to send messages even before they are created. This means the messages are queued.
Unfortunately, it means that if the actor is terminated and thequeue is removed, clients could still recreate the queue and cause an OOM.
To avoid this, when named actors are created "queue protection" can be activated. This will create a fake queue that does not accept new messages. Unfortunately it still use some small memory, for each actor.

In practice, if you plan to have millions of named actors you could either:
- call *ActorSystem.sendMessage()* with forceDelivery==false, and avoid queue protection, which would save memory but would not allow clients to send messages before the actor is created.
- call *ActorSystem.sendMessage()* with forceDelivery==true, and use queue protection, which would use some more memory while allowing clients to send messages before the actor is created.


  
 
 

Actor Pools
===
Fibry supports the concept of actor pool, a scalable pool of actors than can quickly scale based on the number of messages in the queue.
The pools can be created using the class ActorSystem. However, please be careful because some operations might behave differently than with other actors. In particular thread confinement will no longer work as before, because your code can run on multiple actors. However, as long as you access the state of the actor, you are guaranteed that the state is thread confined.
When creating a pool, you get access to the PoolActorLeader, which is a representative of the group but does not really process messages. If the pool is scalable, another actor is created to monitor the work queue. So creating a pool of N actors might actually create N=1 or N+2 actors.
The leader can be used as a normal actor, and will take care to send the messages to the workers. 

This code creates a fixed pool of 3 actors:
```java
var leader = ActorSystem.anonymous().<String>poolParams(PoolParameters.fixedSize(3), null).<String>newPool(actorLogic);

```  

And the following code creates a scalable pool from 3 to 10 actors:
```java
var leader = ActorSystem.anonymous().<String>poolParams(PoolParameters.scaling(3, 10, 100, 0, 1, 500), null).<String>newPool(actorLogic);
```

Map-Reduce
===
Fibry implements two types of map-reduce: unbounded (one actor per computation) or bounded (backed by an acotrs pool).

The following code a map-reduce job with 4 mappers that compute the square of a number, and one reduced that sum the results:

```java
MapReducer<Integer, Integer> mr = Stereotypes.def().mapReduce(PoolParameters.fixedSize(4), (Integer n) -> n * n, Integer::sum, 0);

mr.map(1,2,3,4,5);
assertEquals(Integer.valueOf(55), mr.get(true));
```

The following code does the same using one actor per computation, and a more compact syntax:
```java
int res = Stereotypes.def().mapReduce((Integer n) -> n * n, Integer::sum, 0).map(1,2,3,4,5).get(true);

assertEquals(55, res);
```

Pub/Sub
===
Fibry provides a very simple Pub/Sub system, through the PubSub class, with different strategies affecting the number of actors involved. The Pub/Sub system creates actors using the default strategy.
The following is a "Hello World" using Pub/Sub:
```java
PubSub<String> ps = PubSub.oneActorPerTopic();

ps.subscribe("test", System.out::println);
ps.publish("test", "HelloWorld!");
```

Pub/Sub can help decoupling components, reducing latency (as tasks can be processed by actors asynchronously) and transparently adding/removing logging and monitoring, even at runtime.
Applications using WebSockets or Queues might also benefit from Pub/Sub, as their domain is event based. 

Utilities
===
Fibry has a file called Utilities that contains some useful methods.
Listening to changes in a directory can be a bit difficult in Java. Fibry provide the *Utilities.watchDirectory()* method, that makes it simpler to receive event about file changes.
 
Some warnings
===
Fibry is experimental, and to leverage its potential you need to use Loom, which is a project under development that it's not clear when it will be merged into the OpenJDK; that said, the development of Loom seems very active and proceeding well.
Loom might still have some bugs, as I saw some errors popping up when exchanging sync messages between a thread and a fiber, so it might be better to not mix them for now.
If you start to use Fibry and find some bugs, please notify me.
The API is going to change a bit, while I start to use it in more projects. Nothing drastic, but you might find a new parameter in some methods. I apologise for that, but it is necessary.

As of today, not every network operation is *fiber friendly*. You can find a list of what works and what does not [here](https://wiki.openjdk.java.net/display/loom/Networking+IO). 
In particular UDP is only partially supported. Selectors are also not supported, but as avoiding non-blocking operation is a key goal of fibers, this should not be a concern.

At the moment I have no plans to make a distributed version of Fibry, but if there is real interest I would be happy to do it.

Enjoy!

Acknowledgements
===
Big thank you to Deniz Türkoglu: his code, advise, code reviews and endless discussions made Fibry a much better product. 