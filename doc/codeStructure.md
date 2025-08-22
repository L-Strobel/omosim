# Code Structure

## de.uniwuerzburg.omosim.core

Implementations of the stochastic models.

Central class: [Omosim](../src/main/kotlin/de/uniwuerzburg/omosim/core/Omosim.kt)

Important Interfaces:
- [AgentFactory](../src/main/kotlin/de/uniwuerzburg/omosim/core/AgentFactory.kt): Creates the agent population by assigning socio-demographic features, as well as home, work, and school locations.
- [DestinationFinder](../src/main/kotlin/de/uniwuerzburg/omosim/core/DestinationFinder.kt): Handles destination choice
- [ActivityGenerator](../src/main/kotlin/de/uniwuerzburg/omosim/core/ActivityGenerator.kt): Determines the type of activities an agent undertakes and their durations  
- [CarOwnership](../src/main/kotlin/de/uniwuerzburg/omosim/core/CarOwnership.kt): Determines if an agent owns a car or not
- [ModeChoice](../src/main/kotlin/de/uniwuerzburg/omosim/core/ModeChoice.kt): Determines mode choice

Implementations of each of these interfaces can be swapped inside the omosim class.
For example, the omosim class holds a DestinationFinder;
if you provide a new implementation of the DestinationFinder Interface and replace omosims DestinationFinder
with it, then destination choice will be made according to your new method throughout the simulation.

### de.uniwuerzburg.omosim.core.models

Includes basic data structures with no or only basic internal logic,
like enumerations and locations.

## de.uniwuerzburg.omosim.io

Handles IO.

## de.uniwuerzburg.omosim.routing

Includes a wrapper around GraphHopper and caching logic.

## de.uniwuerzburg.omosim.utils

Miscellaneous utilities.