- Adds AutoHealing, only for threads, which:
  - Tries to interrupt an actor
  - If it is not possible, a new actor is created (reusing the same queue, so no messages are lost, except possibly the one freezing the thread)
  - If the frozen job eventually finishes, the thread is disposed  
 