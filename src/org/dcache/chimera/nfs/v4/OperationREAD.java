package org.dcache.chimera.nfs.v4;

import java.nio.ByteBuffer;
import org.dcache.chimera.nfs.v4.xdr.nfsstat4;
import org.dcache.chimera.nfs.v4.xdr.nfs_argop4;
import org.dcache.chimera.nfs.v4.xdr.nfs_opnum4;
import org.dcache.chimera.nfs.v4.xdr.READ4resok;
import org.dcache.chimera.nfs.v4.xdr.READ4res;
import org.dcache.chimera.nfs.ChimeraNFSException;
import org.apache.log4j.Logger;
import org.dcache.chimera.ChimeraFsException;
import org.dcache.chimera.IOHimeraFsException;
import org.dcache.chimera.posix.AclHandler;
import org.dcache.chimera.posix.Stat;
import org.dcache.chimera.posix.UnixAcl;

public class OperationREAD extends AbstractNFSv4Operation {

	private static final Logger _log = Logger.getLogger(OperationREAD.class.getName());

	public OperationREAD(nfs_argop4 args) {
		super(args, nfs_opnum4.OP_READ);
	}

	@Override
	public boolean process(CompoundContext context) {
        READ4res res = new READ4res();


        try {

            if( context.currentInode().isDirectory() ) {
                throw new ChimeraNFSException(nfsstat4.NFS4ERR_ISDIR, "path is a directory");
            }

            if( context.currentInode().isLink() ) {
                throw new ChimeraNFSException(nfsstat4.NFS4ERR_INVAL, "path is a symlink");
            }

            Stat inodeStat = context.currentInode().statCache();

            UnixAcl fileAcl = new UnixAcl(inodeStat.getUid(), inodeStat.getGid(),inodeStat.getMode() & 0777 );
            if ( ! _permissionHandler.isAllowed(fileAcl, context.getUser(), AclHandler.ACL_READ)  ) {
                throw new ChimeraNFSException( nfsstat4.NFS4ERR_ACCESS, "Permission denied."  );
            }


            NFSv4StateHandler.getInstace().updateClientLeaseTime(_args.opread.stateid);


            long offset = _args.opread.offset.value.value;
            int count = _args.opread.count.value.value;

            ByteBuffer buf = ByteBuffer.allocate(count);

            int bytesReaded = context.currentInode().read(offset, buf.array(), 0, count);
            if( bytesReaded < 0 ) {
                throw new IOHimeraFsException("IO not allowd");
            }

            res.status = nfsstat4.NFS4_OK;
            res.resok4 = new READ4resok();

            res.resok4.data = buf;

            if( offset + bytesReaded >= inodeStat.getSize() ) {
                res.resok4.eof = true;
            }

        }catch(IOHimeraFsException hioe) {
        	if(_log.isDebugEnabled() ) {
        		_log.debug("READ: " + hioe.getMessage() );
        	}
            res.status = nfsstat4.NFS4ERR_IO;
        }catch(ChimeraNFSException he) {
        	if(_log.isDebugEnabled() ) {
        		_log.debug("READ: " + he.getMessage() );
        	}
            res.status = he.getStatus();
        }catch(ChimeraFsException hfe) {
            res.status = nfsstat4.NFS4ERR_NOFILEHANDLE;
        }


       _result.opread = res;

            context.processedOperations().add(_result);
            return res.status == nfsstat4.NFS4_OK;
	}

}