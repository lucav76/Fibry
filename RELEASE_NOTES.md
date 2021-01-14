- Improved support for actors with bounded queue:
  - execute() now waits indefinitely, instead of throwing an exception
  - sendPoisonPill() now waits indefinitely, instead of simply returning false
  - added execAsyncTimeout(), that does not throw an exception
- Backport of generators to Java 8
 