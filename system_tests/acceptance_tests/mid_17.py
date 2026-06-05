import tomllib
import pandas as pd

with open("resources/config.local.toml", "rb") as f:
    file_locations = tomllib.load(f)

def addODPurps(trips):
    # Sort MID
    trips = trips.sort_values(["HP_ID_Lok", "W_ID"])

    sos = trips.W_SO1.values
    wids = trips.W_ID.values
    purposes = trips.zweck.values

    def getDestByPurp(j):
        if purposes[j] in [1]:
            return "W"  # Work
        elif purposes[j] in [3]:
            return "S" # School
        elif purposes[j] in [4]:
            return "P" # Shopping
        elif purposes[j] in [2, 5, 6, 7, 10]: # Now with buisness
            return "O"
        elif purposes[j] in [8]:
            return "H"
        elif purposes[j] in [9]:
            if wids[j] == 1:
                return None
            else:
                return getDestByPurp(j-1)
        else:
            return None

    startLocs = []
    destLocs = []
    for i in range(len(sos)):
        destLocs.append(getDestByPurp(i))

        startLoc = None
        if wids[i] == 1:
            if sos[i] == 1:
                startLoc = "H"  # Home
            elif sos[i] in [2]:
                startLoc = "O"  # Other
        else:
            startLoc = destLocs[i-1]
        startLocs.append(startLoc)

    trips["StartLoc"] = startLocs
    trips["DestLoc"] = destLocs
    return trips

def imputeCells(trips):
    # Sort MID
    trips = trips.sort_values(["HP_ID_Lok", "W_ID"])

    sos = trips.W_SO1.values
    wids = trips.W_ID.values
    pids = trips.HP_ID_Lok.values
    purposes = trips.zweck.values
    distance = trips.wegkm_imp.values

    homeCells = trips.GITTER_500m.values

    startCells = trips.GITTER_SO_500m.values
    stopCells = trips.GITTER_ZO_500m.values
    for i in range(len(sos)):
        homeCell = homeCells[i]

        startCell = startCells[i]
        stopCell = stopCells[i]

        if startCell == " ":
            if wids[i] == 1:
                if (homeCell != " ") and (sos[i] == 1):
                    startCell = homeCell
            else:
                if (stopCells[i-1] != " ") and (wids[i-1] + 1 == wids[i]) and (pids[i-1] == pids[i]):
                    startCell = stopCells[i-1]
        if stopCell == " ":
            if (startCells[i+1] != " ") and (wids[i] + 1 == wids[i+1]) and (pids[i+1] == pids[i]):
                stopCell = startCells[i+1]
            elif (homeCell != " ") and (purposes[i] == 8):
                stopCell = homeCell
            elif (startCell != " ") and (distance[i] < 0.25):
                stopCell = startCell

        startCells[i] = startCell
        stopCells[i] = stopCell

    return trips

def get_mid_17():
    fn_mid_trips = file_locations["local_paths"]["mid17_trips"]
    fn_mid_persons = file_locations["local_paths"]["mid17_persons"]

    lngNameMap = {"W": "WORK", "B": "BUSINESS", "S": "SCHOOL", "P": "SHOPPING", "O": "OTHER", "H": "HOME"}

    trips = pd.read_csv(fn_mid_trips, sep=";", decimal=",")

    # Drop regular work trips. They do not appear in order and have no timing information.
    trips = trips[trips.W_RBW != 1]

    # Impute information
    trips = imputeCells(trips)
    trips = addODPurps(trips)

    trips.StartLoc = trips.StartLoc.apply(lambda x: lngNameMap[x] if x is not None else None)
    trips.DestLoc =  trips.DestLoc.apply(lambda x: lngNameMap[x] if x is not None else None)

    trips = trips[(trips.GITTER_SO_500m != "500mN27830E44360") & (trips.GITTER_ZO_500m != "500mN27830E44360") & (trips.GITTER_SO_1km != "1kmN3379E4319") & (trips.GITTER_ZO_1km != "1kmN3379E4319")] # Delete faulty cell

    trips = trips.rename(columns={"StartLoc": "startAct", "DestLoc": "stopAct"})

    persons = pd.read_csv(fn_mid_persons, sep=";", decimal=",")
    return trips, persons