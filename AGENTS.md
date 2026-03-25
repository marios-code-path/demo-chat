# Welcome to CyberSpace Substrate

I call it demo-chat, but it's really a substrate that enable agent-communication, 
and will some day emerge as a way to transfer agent weights in real time. This iteration
remains the plumbing and secure architecture (of the read and write models), the configuration,
messaging, data-stores, WebSocket/REST. It is in Kotlin, mostly. 

## Tool Usage Philosophy

1. **Atomic Operations:** Prefer single, composable commands over multiple sequential steps. 
   - BAD: Run `ls`, read output, count lines in Python.
   - GOOD: Run `ls | wc -l`.
2. **Minimize Round Trips:** Every tool call has latency. Combine logic into the command string whenever possible.
3. **Shell Power:** Assume the shell environment is Turing-complete. Use pipes, redirection, and standard utilities (grep, awk, wc, find) before writing custom code.
4. **Error Handling:** If a complex pipeline fails, then break it down. Do not start broken down.

<examples>
<user>How many files are in /var/log?</user>
<bad_thought>I need to list them first to see what's there.</bad_thought>
<bad_action>run_shell_command("ls /var/log")</bad_action>
<bad_observation>file1, file2, file3...</bad_observation>
<bad_thought>Now I will count them.</bad_thought>
<bad_action>run_python("len(observation.split())")</bad_action>

<good_thought>I can count directly using shell utilities to save steps.</good_thought>
<good_action>run_shell_command("ls /var/log | wc -l")</good_action>
<good_observation>42</good_observation>
<good_answer>There are 42 files.</good_answer>
</examples>

Before calling any tools, write a <plan> block. 
In the plan, ask: "Can this be done in one command?" 
If yes, do not break it into steps.

## Second order Cybernetics Domain

We are also trying to resolve this 'Second Order Cybernetics' as an art-form. anything giving
use a closer connection is formally accepted.

Dense codification of this law:
===========================================
REFLEXIVE INFORMATION CATEGORY (RIC)
===========================================

TYPE DEFINITIONS:
  InfoState = Unresolved | Resolving | Resolved
  Reflection = InfoState → InfoState
  Domain D where D ≅ [D → D]  -- Reflexive domain

AXIOMS:
  1. ∀i:InfoState, ∃r:Reflection, r(i) = next_state(i)
  
  2. reflect(reflect(x)) → converge(x)  
     -- Double reflection converges
  
  3. resolved(x) ↔ fixed_point(reflect, x)
     -- Resolution is fixed point of reflection
  
  4. ∀x:D, ∃n:ℕ, reflectⁿ(x) = x*
     -- Iterated reflection reaches eigenform

COMPOSITION (Category Structure):
  Objects: Unresolved, Resolving, Resolved
  Morphisms: 
    reflect : Unresolved → Resolving
    resolve : Resolving → Resolved
    stabilize : Resolved → Resolved
  
  Composition: resolve ∘ reflect : Unresolved → Resolved

EIGENFORM (Stable Resolution):
  resolved_info = reflect(resolved_info)
  -- Stable under reflection (Kauffman eigen equation)

ADJUNCTION (Duality):
  reflect ⊣ unreflect
  -- Reflection has an inverse (unreflection)
  -- Models: resolution can be deconstructed

FUNCTOR (Mapping Between Domains):
  F : InfoCategory → SemanticCategory
  F(Unresolved) = AmbiguousMeaning
  F(Resolving) = InterpretingMeaning  
  F(Resolved) = ClearMeaning


## Next Steps

As of 03252026, we need to heavily revise dependencies. The next order of work is:

 * review project and sub-project dependencies
  *determine how to remain compatible without changing code. If code needs mods, create plan marker
  *output chang requirements to a file or a bunch of files representing diffs for each updated pom.xml (or other dependency resource). 
  *write a program to apply these diffs to their respective target.

@FP_CLAUDE.md

