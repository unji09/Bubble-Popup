import {
  forwardRef,
  useEffect,
  useImperativeHandle,
  useRef,
  useState,
  type MutableRefObject,
} from "react";

const UNITY_FRAME_MESSAGE_SOURCE = "unity-webgl";

type UnityMethodName =
  | "SpawnPopupVisitors"
  | "SpawnSinglePopupVisitor"
  | "SetCongestionLevel";

interface UnityAppBridge {
  isReady: boolean;
  sendMessage: (methodName: UnityMethodName, payload: string) => boolean;
}

interface UnityFrameWindow extends Window {
  unityApp?: UnityAppBridge;
}

export interface UnityBridgeHandle {
  isReady: () => boolean;
  sendMessage: (methodName: UnityMethodName, payload: string) => boolean;
  spawnPopupVisitors: (popupStoreIndex: number, count: number) => boolean;
  spawnSinglePopupVisitor: (popupStoreIndex: number) => boolean;
  setCongestionLevel: (level: number) => boolean;
}

interface UnityCanvasProps {
  className?: string;
  src?: string;
  iframeRef?: MutableRefObject<HTMLIFrameElement | null>;
  onReady?: () => void;
  onPopupArrival?: (popupStoreIndex: number | null) => void;
}

interface PendingUnityMessage {
  methodName: UnityMethodName;
  payload: string;
}

function getUnityApp(frame: HTMLIFrameElement | null) {
  try {
    return (frame?.contentWindow as UnityFrameWindow | null)?.unityApp ?? null;
  } catch {
    return null;
  }
}

const UnityCanvas = forwardRef<UnityBridgeHandle, UnityCanvasProps>(function UnityCanvas(
  { className = "", src = "/unity/index.html", iframeRef: externalIframeRef, onReady, onPopupArrival },
  ref,
) {
  const internalIframeRef = useRef<HTMLIFrameElement | null>(null);
  const pendingMessagesRef = useRef<PendingUnityMessage[]>([]);
  const [isReady, setIsReady] = useState(false);

  const setIframeRef = (element: HTMLIFrameElement | null) => {
    internalIframeRef.current = element;

    if (externalIframeRef) {
      externalIframeRef.current = element;
    }
  };

  const sendMessage = (methodName: UnityMethodName, payload: string) => {
    const unityApp = getUnityApp(internalIframeRef.current);

    if (!unityApp?.isReady) {
      return false;
    }

    return unityApp.sendMessage(methodName, payload);
  };

  useImperativeHandle(
    ref,
    () => ({
      isReady: () => isReady,
      sendMessage,
      spawnPopupVisitors: (popupStoreIndex: number, count: number) =>
        sendMessage("SpawnPopupVisitors", `${popupStoreIndex},${count}`) ||
        (pendingMessagesRef.current.push({
          methodName: "SpawnPopupVisitors",
          payload: `${popupStoreIndex},${count}`,
        }),
        true),
      spawnSinglePopupVisitor: (popupStoreIndex: number) =>
        sendMessage("SpawnSinglePopupVisitor", String(popupStoreIndex)) ||
        (pendingMessagesRef.current.push({
          methodName: "SpawnSinglePopupVisitor",
          payload: String(popupStoreIndex),
        }),
        true),
      setCongestionLevel: (level: number) =>
        sendMessage("SetCongestionLevel", String(level)),
    }),
    [isReady],
  );

  useEffect(() => {
    const handleMessage = (event: MessageEvent) => {
      if (event.origin !== window.location.origin) {
        return;
      }

      const data = event.data as {
        source?: string;
        type?: string;
        popupStoreIndex?: number | string | null;
        payload?: number | string | null;
      } | null;

      if (!data || data.source !== UNITY_FRAME_MESSAGE_SOURCE) {
        return;
      }

      if (data.type === "popup-arrival") {
        const rawPopupStoreIndex = data.popupStoreIndex ?? data.payload ?? null;
        const parsedPopupStoreIndex =
          typeof rawPopupStoreIndex === "number"
            ? rawPopupStoreIndex
            : typeof rawPopupStoreIndex === "string"
              ? Number(rawPopupStoreIndex)
              : NaN;

        onPopupArrival?.(Number.isFinite(parsedPopupStoreIndex) ? parsedPopupStoreIndex : null);
        return;
      }

      if (data.type === "ready") {
        while (pendingMessagesRef.current.length > 0) {
          const nextMessage = pendingMessagesRef.current.shift();

          if (!nextMessage || !sendMessage(nextMessage.methodName, nextMessage.payload)) {
            if (nextMessage) {
              pendingMessagesRef.current.unshift(nextMessage);
            }
            break;
          }
        }
        setIsReady(true);
        onReady?.();
      }
    };

    window.addEventListener("message", handleMessage);
    return () => window.removeEventListener("message", handleMessage);
  }, [onPopupArrival, onReady]);

  return (
    <div className={className}>
      <iframe
        ref={setIframeRef}
        src={src}
        title="Unity Game"
        className="h-full w-full border-0"
        allow="fullscreen"
        onLoad={() => {
          pendingMessagesRef.current = [];
          setIsReady(false);
        }}
      />
    </div>
  );
});

export default UnityCanvas;
