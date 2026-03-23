# Introduction 

Explain the expected nomadic merchant task execution.

# Task Pre-requesite

Must be on the world or home screen, to have shop button appear.

# Task Execution

Action : Tap the shop Button.  
Result : The nomadic Marchant screen appear.

    do
        do
            for each (wood, coal, meat, iron) search for a card . if found tap it
            (option) Search for a VIP card. if found tap it.
        while (a card was found)

        if(free refresh available) tap it.
    while(free refresh was taped)

# Task End

reschedule for next day after reset.

# Exit cases : 

Success - a search has been executed, for a free resources, a vip point, a free refresh. The task exit without purchase.  
Success - a search has been executed, for a free resources, a vip point, a free refresh. The task exit with at least one purchase.  
Fail - shop button not found.

# Failure case not handled :

- Free Ressource purchase failed.
- VIP point purchase tap failed.

To avoid unamaged error leading to endless loop, task limits its execution to 2 minutes.

- Tap daily refresh failed (not refreshed). In that case the task will end without purchasing all it could.

# Stat

A log will display at each execution : 
  - How many card not paid were taped
  - How many VIP card were taped.
  - Hown many refresh called.

The statistic screen will display the stat for the last run.
Those stat help appreciate the task efficiency, and detect issue by anyone.

