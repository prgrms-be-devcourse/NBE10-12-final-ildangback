import { useCallback, useEffect, useRef, useState } from "react";
import {
  getNotifications,
  readNotification,
  type NotificationResponse,
} from "../api";
import { groupErrorMessage } from "../../group/errors";

export function useNotifications() {
  const [notifications, setNotifications] = useState<NotificationResponse[]>(
    [],
  );
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [pendingId, setPendingId] = useState<number | null>(null);
  const busy = useRef(false);
  const generation = useRef(0);
  const reload = useCallback(async () => {
    if (busy.current) return;
    const request = ++generation.current;
    setLoading(true);
    setError(null);
    try {
      const result = await getNotifications();
      if (request === generation.current)
        setNotifications(result.filter((item) => item.readAt === null));
    } catch (err) {
      if (request === generation.current) setError(groupErrorMessage(err));
    } finally {
      if (request === generation.current) setLoading(false);
    }
  }, []);
  useEffect(() => {
    let cancelled = false;
    const request = ++generation.current;
    getNotifications()
      .then((result) => {
        if (!cancelled && request === generation.current)
          setNotifications(result.filter((item) => item.readAt === null));
      })
      .catch((err) => {
        if (!cancelled && request === generation.current)
          setError(groupErrorMessage(err));
      })
      .finally(() => {
        if (!cancelled && request === generation.current) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const markRead = async (notification: NotificationResponse) => {
    if (busy.current) return false;
    if (notification.readAt !== null) {
      setNotifications((items) =>
        items.filter((item) => item.id !== notification.id),
      );
      return true;
    }
    busy.current = true;
    const request = ++generation.current;
    setLoading(false);
    setPendingId(notification.id);
    setError(null);
    try {
      await readNotification(notification.id);
      if (request !== generation.current) return false;
      setNotifications((items) =>
        items.filter((item) => item.id !== notification.id),
      );
      return true;
    } catch (err) {
      if (request === generation.current) setError(groupErrorMessage(err));
      return false;
    } finally {
      busy.current = false;
      setPendingId(null);
    }
  };
  const unreadCount = notifications.length;
  return {
    notifications,
    unreadCount,
    loading,
    error,
    pendingId,
    reload,
    markRead,
  };
}
