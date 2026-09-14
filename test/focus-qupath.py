from Xlib import display, X
from Xlib.protocol import event
import time
conn=display.Display(); root=conn.screen().root
for wid in root.get_full_property(conn.intern_atom('_NET_CLIENT_LIST'),X.AnyPropertyType).value:
    win=conn.create_resource_object('window',wid)
    if (win.get_wm_name() or '').startswith('QuPath - synthetic-8.tif'):
        root.send_event(event.ClientMessage(window=win,client_type=conn.intern_atom('_NET_ACTIVE_WINDOW'),data=(32,[2,X.CurrentTime,0,0,0])),event_mask=X.SubstructureRedirectMask|X.SubstructureNotifyMask)
        conn.sync();time.sleep(.4);break
else: raise RuntimeError('Synthetic QuPath window not found')
