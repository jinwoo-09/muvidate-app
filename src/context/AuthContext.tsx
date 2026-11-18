import React, { createContext, useContext, useEffect, useState } from "react";
import { User, onAuthStateChanged } from "firebase/auth";
import { 
  auth, 
  getUserProfile, 
  registerWithUsernameAndPassword,
  loginWithUsernameAndPassword,
  setAccountPassword,
  updateUserProfilePhoto,
  logoutUser
} from "../lib/firebase";
import { UserProfile } from "../types";

const PROFILE_CACHE_PREFIX = "muvidate_profile_cache_";

function readCachedProfile(uid: string): UserProfile | null {
  try {
    const raw = localStorage.getItem(`${PROFILE_CACHE_PREFIX}${uid}`);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    return parsed && parsed.username ? (parsed as UserProfile) : null;
  } catch {
    return null;
  }
}

function writeCachedProfile(uid: string, prof: UserProfile | null): void {
  try {
    if (prof) {
      localStorage.setItem(`${PROFILE_CACHE_PREFIX}${uid}`, JSON.stringify(prof));
    } else {
      localStorage.removeItem(`${PROFILE_CACHE_PREFIX}${uid}`);
    }
  } catch {
    // Ignore storage quota errors
  }
}

interface AuthContextType {
  user: User | null;
  profile: UserProfile | null;
  loading: boolean;
  needsUsernameSetup: boolean;
  saveUsername: (username: string, password?: string) => Promise<void>;
  loginUser: (username: string, password: string) => Promise<void>;
  setPassword: (password: string) => Promise<void>;
  updatePhoto: (photoUrl: string) => Promise<void>;
  refreshProfile: () => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [needsUsernameSetup, setNeedsUsernameSetup] = useState<boolean>(false);

  useEffect(() => {
    let settled = false;
    // Failsafe so startup splash never blocks indefinitely if Firebase is offline
    const fallbackTimer = window.setTimeout(() => {
      if (!settled) {
        setLoading(false);
      }
    }, 3000);

    // onAuthStateChanged waits for Firebase Auth to restore any persisted session from IndexedDB/localStorage
    const unsubscribe = onAuthStateChanged(auth, async (currentUser) => {
      settled = true;
      window.clearTimeout(fallbackTimer);
      setUser(currentUser);
      if (currentUser) {
        const cached = readCachedProfile(currentUser.uid);
        if (cached) {
          setProfile(cached);
          setNeedsUsernameSetup(false);
          setLoading(false);
        }
        try {
          const userProf = await Promise.race([
            getUserProfile(currentUser.uid),
            new Promise<null>((resolve) => window.setTimeout(() => resolve(null), 4000))
          ]);
          if (userProf && userProf.username) {
            setProfile(userProf);
            writeCachedProfile(currentUser.uid, userProf);
            setNeedsUsernameSetup(false);
          } else if (!cached) {
            setProfile(null);
            setNeedsUsernameSetup(true);
          }
        } catch (err) {
          console.error("Error reading user profile:", err);
          if (!cached) {
            setNeedsUsernameSetup(true);
          }
        }
      } else {
        setProfile(null);
        setNeedsUsernameSetup(true);
      }
      setLoading(false);
    });

    return () => {
      window.clearTimeout(fallbackTimer);
      unsubscribe();
    };
  }, []);

  const saveUsername = async (username: string, password?: string) => {
    const newProfile = await registerWithUsernameAndPassword(username, password);
    setProfile(newProfile);
    if (newProfile?.uid) writeCachedProfile(newProfile.uid, newProfile);
    setNeedsUsernameSetup(false);
  };

  const loginUser = async (username: string, password: string) => {
    const loggedProfile = await loginWithUsernameAndPassword(username, password);
    setProfile(loggedProfile);
    if (loggedProfile?.uid) writeCachedProfile(loggedProfile.uid, loggedProfile);
    setNeedsUsernameSetup(false);
  };

  const setPassword = async (password: string) => {
    await setAccountPassword(password);
  };

  const updatePhoto = async (photoUrl: string) => {
    if (!user) throw new Error("No active user session.");
    await updateUserProfilePhoto(user.uid, photoUrl);
    setProfile(prev => {
      const next = prev ? { ...prev, photoURL: photoUrl } : null;
      if (next) writeCachedProfile(user.uid, next);
      return next;
    });
  };

  const refreshProfile = async () => {
    if (!user) return;
    const p = await getUserProfile(user.uid);
    if (p) {
      setProfile(p);
      writeCachedProfile(user.uid, p);
    }
  };

  const logout = async () => {
    const currentUid = user?.uid;
    await logoutUser();
    if (currentUid) writeCachedProfile(currentUid, null);
    setUser(null);
    setProfile(null);
    setNeedsUsernameSetup(true);
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        profile,
        loading,
        needsUsernameSetup,
        saveUsername,
        loginUser,
        setPassword,
        updatePhoto,
        refreshProfile,
        logout
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextType {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}
