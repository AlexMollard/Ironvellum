#!/usr/bin/env python3
"""Measures what the Margin crest (Close Ranks) and the Ironvellum crest (Deep Vault) add to the vault stack.

    python3 tools/crest_margin_sim.py

Simulates 4,000 vaults at 20, 39 and 59 draws with the real drop table and pity (Gacha.DROP_TABLE: a forced
relic every 5th draw, a forced non-echo draw after 3 echo draws, a Common first draw lifted to Rare) and prints
how much each perk lifts Relics.effectiveMultiplier on those vaults. The stack multiplies the whole rate,
so these are percent changes in essence an hour for a lifter with that vault.

Close Ranks weighs every backing relic x1.25 (restWeight); Deep Vault raises the excess ceiling from x3 to x5.
The comment on the "margin" crest in Crests.kt quotes these numbers (+3.5% / +4.2% / +4.5% for Close Ranks).
"""
import math
import random

# (chance, figureChance, figuresLow, figuresHigh, relicChance, relicLow, relicHigh, tier): Gacha.DROP_TABLE.
TABLE = [(.60, .62, 20, 40, .33, 1.05, 1.15, 'C'), (.30, .35, 60, 120, .50, 1.15, 1.35, 'R'),
         (.09, .25, 200, 400, .50, 1.35, 1.75, 'E'), (.01, .15, 800, 1500, .60, 1.75, 2.5, 'M')]
# RelicHouses.CATALOGUE: (house, tier) in the frozen order.
CAT = [('iron', 'C'), ('iron', 'E'), ('iron', 'R'), ('iron', 'R'), ('vigil', 'C'), ('vigil', 'C'), ('vigil', 'R'),
       ('vigil', 'E'), ('craft', 'R'), ('craft', 'E'), ('craft', 'C'), ('craft', 'M'), ('ret', 'C'), ('ret', 'C'),
       ('ret', 'R'), ('ret', 'R')]


def eff(ms,k=1.0,capx=3.0):
    ms=sorted([m for m in ms if m>1],reverse=True)
    if not ms: return 1.0
    best=ms[0]-1; rest=k*sum((m-1)/(i+2) for i,m in enumerate(ms[1:]))
    head=best*(capx-1); con=head*(1-math.exp(-rest/head)) if head>0 else 0
    return 1+best+con
def vaults(draws,trials=4000,seed=1):
    rng=random.Random(seed); out=[]
    for _ in range(trials):
        owned={}; fs=0; rs=0; first=True
        for d in range(draws):
            r=rng.random(); cum=0
            for row in TABLE:
                cum+=row[0]
                if r<cum: break
            odds=row
            if first and odds[7]=='C': odds=TABLE[1]
            forcedRelic=first or rs>=4; forced=fs>=3
            t=rng.random(); v=rng.random()
            def relic():
                tier=odds[7]; cells=[i for i,c in enumerate(CAT) if c[1]==tier]
                open_=[i for i in cells if i not in owned]; pool=open_ or cells
                c=rng.choice(pool); lo,hi=odds[5],odds[6]
                if c not in owned: owned[c]=lo+(hi-lo)*v; return 'relic'
                owned[c]=min(hi,owned[c]+(hi-lo)/4); return 'relic'
            if forcedRelic: res=relic()
            elif forced:
                rsh=odds[4]/(odds[4]+(1-odds[1]-odds[4])); res=relic() if t<rsh else 'frame'
            elif t<odds[1]: res='fig'
            elif t<odds[1]+odds[4]: res=relic()
            else: res='frame'
            fs=fs+1 if res=='fig' else 0; rs=0 if res=='relic' else rs+1; first=False
        out.append(list(owned.values()))
    return out
for n in (20,39,59):
    vs=vaults(n)
    for label,kw in [('close ranks x1.25',dict(k=1.25)),('deep vault cap 5',dict(capx=5.0))]:
        a=sum(eff(v) for v in vs)/len(vs); b=sum(eff(v,**kw) for v in vs)/len(vs)
        print(n,'draws',label,'%+.2f%%'%((b/a-1)*100),'mean relics %.1f'%(sum(len(v) for v in vs)/len(vs)))
