STEPS_GBNF = r'''
root ::= "[" step ("," step)* "]"
step ::= leaf | repeat
repeat ::= "{\"kind\":\"repeat\",\"count\":" small ",\"body\":[" leaf ("," leaf)* "]}"
leaf ::= move | rotate | wait | simple
move ::= "{\"kind\":\"" ("moveForward" | "move") "\",\"distanceM\":" dist "}"
rotate ::= "{\"kind\":\"" ("rotateLeft" | "rotateRight") "\",\"degrees\":" deg "}"
wait ::= "{\"kind\":\"wait\",\"seconds\":" small "}"
simple ::= "{\"kind\":\"" ("stop" | "standUp" | "greet" | "heart" | "dance") "\"}"
dist ::= [1-3] | [0-2] "." [1-9]
deg ::= "360" | "3" [0-5] [0-9] | [12] [0-9] [0-9] | [1-9] [0-9] | [1-9]
small ::= "10" | [1-9]
'''
